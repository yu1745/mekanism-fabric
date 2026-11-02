package mekanism.fabric.recipe.fixture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.ChemicalDataContext;
import mekanism.api.fluid.FluidStack;
import mekanism.api.functions.TriPredicate;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.common.recipe.lookup.cache.InputRecipeCache;
import mekanism.common.recipe.lookup.cache.RecipeCacheContext;
import mekanism.common.recipe.lookup.cache.RecipeCacheSource;
import mekanism.common.registries.MekanismChemicals;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Isolated real-server probe for the recipe ingredient/cache/reload vertical. Registers one disposable recipe type/serializer, loads a fixture recipe with an
 * item compound, fluid tag, and chemical ingredient through the real {@link RecipeManager}, and proves the generic input cache, hot Chemical cache epoch, reload
 * ownership, and manager-identity contracts.
 */
public final class RecipeRuntimeServerFixture implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/RecipeRuntimeServerFixture");
    private static final Path ROOT = Path.of(System.getProperty("mekanism.recipe.runtimeRoot")).toAbsolutePath().normalize();
    public static final ResourceLocation FIXTURE_ID = ResourceLocation.fromNamespaceAndPath("mekanism_recipe_fixture", "fusion");
    private static final ResourceLocation FAILURE_LISTENER_ID =
          ResourceLocation.fromNamespaceAndPath("mekanism_recipe_fixture", "failure_listener");
    private static final ResourceLocation FAILURE_MARKER =
          ResourceLocation.fromNamespaceAndPath("mekanism_recipe_fixture", "reload_failure.marker");
    public static final RecipeType<FixtureRecipe> TYPE = new RecipeType<>() {
    };
    private static volatile RecipeSerializer<FixtureRecipe> serializer;
    private static boolean initialProbed;
    private static boolean reloadStarted;
    private static boolean failedReloadStarted;
    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        //Note: Mod load order is not guaranteed across the production and fixture mods, so force the production
        // chemical/ingredient owners to initialize before any ingredient codec is composed
        mekanism.fabric.chemical.FabricChemicalLifecycle.initialize();
        mekanism.fabric.chemical.FabricChemicalIngredientTypeRegistryLifecycle.initialize();
        Registry.register(BuiltInRegistries.RECIPE_TYPE, FIXTURE_ID, TYPE);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, FIXTURE_ID, serializer());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FailureReloadListener());
        ServerLifecycleEvents.SERVER_STARTED.register(registered -> server = registered);
        ServerTickEvents.END_SERVER_TICK.register(registered -> {
            //Note: SERVER_STARTED handler order across mods is not guaranteed, so the probe runs on the first
            // server tick, after every start handler has bound the production recipe context
            if (server == registered && !initialProbed) {
                initialProbed = true;
                initialProbe(registered);
                marker("server-initial.complete");
            }
            if (!reloadStarted && Files.isRegularFile(ROOT.resolve("reload.trigger"))) {
                reloadStarted = true;
                startSuccessfulReload(registered);
            }
            if (!failedReloadStarted && Files.isRegularFile(ROOT.resolve("fail-reload.trigger"))) {
                failedReloadStarted = true;
                startFailingReload(registered);
            }
        });
    }

    private static void initialProbe(MinecraftServer current) {
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        require(binding != null, "server recipe context must be bound at start");
        require(binding.epoch() == 1, "initial server recipe epoch must be one");
        require(binding.manager() == current.getRecipeManager(), "bound manager must be the actual server recipe manager");
        //The binding must carry the server's own registry access, which is what synthetic vanilla smelting needs instead of a process-global fallback.
        require(binding.registryAccess() == current.registryAccess(), "bound registry access must be the server's own registry access");

        //Canonical owner proof: the API resolves the common owner's single instance, and the owner is a nominal vanilla RecipeType.
        require(mekanism.api.recipes.MekanismRecipeTypes.TYPE_CRUSHING.value()
              == mekanism.common.recipe.MekanismRecipeType.CRUSHING.getRecipeType(),
              "the API crushing holder must be the common owner's canonical instance");
        require(BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.fromNamespaceAndPath("mekanism", "smelting"))
              == mekanism.common.recipe.MekanismRecipeType.SMELTING.getRecipeType(),
              "the registered smelting type must be the canonical owner instance");
        //The owner's own recipe source feeds its input cache, including synthetic vanilla smelting resolved through the published access.
        List<RecipeHolder<mekanism.api.recipes.ItemStackToItemStackRecipe>> smelting =
              mekanism.common.recipe.MekanismRecipeType.SMELTING.getRecipeType().getRecipes(current.getRecipeManager(), current.registryAccess());
        long synthetic = smelting.stream().filter(holder -> holder.id().getPath().startsWith("/mekanism_generated/")).count();
        require(synthetic > 0, "a real server must expose synthetic vanilla smelting recipes through the owner's own source");
        require(smelting.stream().allMatch(holder -> holder.value().getType()
              == mekanism.common.recipe.MekanismRecipeType.SMELTING.getRecipeType()),
              "every synthetic smelting recipe must report the canonical smelting type");
        require(mekanism.common.recipe.MekanismRecipeType.SMELTING.getInputCache()
              .containsInput(current.overworld(), new ItemStack(Items.RAW_IRON)),
              "the smelting input cache must be fed by the owner source including synthetic smelting");

        RecipeManager manager = current.getRecipeManager();
        List<RecipeHolder<FixtureRecipe>> recipes = manager.getAllRecipesFor(TYPE);
        require(recipes.size() == 1, "exactly one fixture recipe must load through the real recipe manager");
        FixtureRecipe recipe = recipes.getFirst().value();
        require(recipes.getFirst().id().equals(ResourceLocation.fromNamespaceAndPath("mekanism_recipe_fixture", "initial")),
              "fixture recipe id mismatch: " + recipes.getFirst().id());
        require(recipe.getItemInput().ingredients().size() == 2, "item compound ingredient must keep two children");
        require(recipe.getItemInput().testType(new ItemStack(Items.OAK_PLANKS)) && recipe.getItemInput().testType(new ItemStack(Items.STICK)),
              "item compound ingredient must match both children");
        require(!recipe.getItemInput().testType(new ItemStack(Items.IRON_INGOT)), "item compound ingredient must not match foreign items");
        require(recipe.getFluidInput().ingredient().test(new FluidStack(Fluids.WATER, 1)), "fluid tag ingredient must match water");
        require(recipe.getChemicalInput().test(new ChemicalStack(MekanismChemicals.HYDROGEN, 10)), "chemical ingredient must match hydrogen");

        InputRecipeCache.ItemFluidChemical<FixtureRecipe> cache = new InputRecipeCache.ItemFluidChemical<>(RecipeCacheSource.forType(TYPE),
              FixtureRecipe::getItemInput, FixtureRecipe::getFluidInput, FixtureRecipe::getChemicalInput);
        FixtureRecipe found = cache.findFirstRecipe(current.overworld(), new ItemStack(Items.STICK),
              new FluidStack(Fluids.WATER, 100), new ChemicalStack(MekanismChemicals.HYDROGEN, 10));
        require(found == recipe, "generic triple input cache must find the fixture recipe");
        require(cache.findFirstRecipe(current.overworld(), new ItemStack(Items.STICK),
              new FluidStack(Fluids.LAVA, 100), new ChemicalStack(MekanismChemicals.HYDROGEN, 10)) == null,
              "a mismatched fluid type must not match");
        require(cache.findFirstRecipe(current.overworld(), new ItemStack(Items.STICK),
              new FluidStack(Fluids.WATER, 100), new ChemicalStack(MekanismChemicals.OXYGEN, 10)) == null,
              "a mismatched chemical type must not match");
        ItemStack named = new ItemStack(Items.STICK);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Named"));
        require(cache.findFirstRecipe(current.overworld(), named,
              new FluidStack(Fluids.WATER, 100), new ChemicalStack(MekanismChemicals.HYDROGEN, 10)) == recipe,
              "component-bearing input stacks still match through the cache");
        require(cache.containsInputA(current.overworld(), new ItemStack(Items.OAK_PLANKS)), "item input containment must work");

        //Repeated chemical queries on the recipe path stay on the exact epoch-one context
        ChemicalDataContext context = ChemicalDataContext.current();
        double radioactivity = MekanismChemicals.NUCLEAR_WASTE.get().getRadioactivity();
        for (int i = 0; i < 32; i++) {
            require(Double.compare(MekanismChemicals.NUCLEAR_WASTE.get().getRadioactivity(), radioactivity) == 0, "repeat chemical queries must be stable");
        }
        require(ChemicalDataContext.current() == context, "repeat queries must retain the exact context instance");
        require(context.epoch() == 1, "chemical data-map publication must not advance the recipe or chemical epoch by itself");

        mismatchedManagerRejected(current, cache);
        LOGGER.info("MEKANISM_RECIPE_RUNTIME_SERVER phase=initial recipe_epoch=1 chemical_epoch={} recipe_loaded=true cache_match=true "
              + "mismatch_rejected=true chemical_stable=true", context.epoch());
    }

    /** A non-null level whose manager differs from the bound one must fail instead of consulting stale state. */
    private static void mismatchedManagerRejected(MinecraftServer current, InputRecipeCache.ItemFluidChemical<FixtureRecipe> cache) {
        RecipeManager foreign = new RecipeManager(current.registryAccess());
        RecipeCacheContext.Binding original = RecipeCacheContext.currentBinding();
        RecipeCacheContext.bind(foreign, 1);
        boolean rejected = false;
        try {
            cache.findFirstRecipe(current.overworld(), new ItemStack(Items.STICK), new FluidStack(Fluids.WATER, 100),
                  new ChemicalStack(MekanismChemicals.HYDROGEN, 10));
        } catch (IllegalStateException expected) {
            rejected = true;
        } finally {
            RecipeCacheContext.bind(original.manager(), original.epoch());
        }
        require(rejected, "a mismatched manager lookup must fail");
        //Note: rebinding the original manager identity discards the foreign probe state and re-initializes from the real manager
        FixtureRecipe found = cache.findFirstRecipe(current.overworld(), new ItemStack(Items.STICK), new FluidStack(Fluids.WATER, 100),
              new ChemicalStack(MekanismChemicals.HYDROGEN, 10));
        require(found != null, "the restored binding must serve the real recipes again");
    }

    private static void startSuccessfulReload(MinecraftServer current) {
        try {
            Files.deleteIfExists(ROOT.resolve("reload.trigger"));
            current.getPackRepository().reload();
            List<String> selected = new ArrayList<>(current.getPackRepository().getSelectedIds());
            String replacement = "file/recipe-runtime-replacement";
            require(current.getPackRepository().getAvailableIds().contains(replacement), "replacement recipe data pack was not discovered");
            selected.remove("file/recipe-runtime");
            selected.add(replacement);
            current.reloadResources(selected).whenComplete((ignored, failure) -> current.execute(() -> {
                if (failure != null) throw new IllegalStateException("recipe runtime reload failed", failure);
                successfulReloadProbe(current);
                marker("reload.complete");
            }));
        } catch (RuntimeException | IOException failure) {
            throw new IllegalStateException("Unable to begin recipe runtime reload", failure);
        }
    }

    private static void successfulReloadProbe(MinecraftServer current) {
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        require(binding.epoch() == 2, "a successful reload must advance the recipe context exactly once to epoch two");
        require(binding.manager() == current.getRecipeManager(), "reloaded binding must keep the server manager identity");
        InputRecipeCache.ItemFluidChemical<FixtureRecipe> cache = new InputRecipeCache.ItemFluidChemical<>(RecipeCacheSource.forType(TYPE),
              FixtureRecipe::getItemInput, FixtureRecipe::getFluidInput, FixtureRecipe::getChemicalInput);
        FixtureRecipe replacement = cache.findFirstRecipe(current.overworld(), new ItemStack(Items.IRON_INGOT),
              new FluidStack(Fluids.WATER, 100), new ChemicalStack(MekanismChemicals.HYDROGEN, 10));
        require(replacement != null, "the replacement recipe must be exposed after the reload");
        require(cache.findFirstRecipe(current.overworld(), new ItemStack(Items.STICK),
              new FluidStack(Fluids.WATER, 100), new ChemicalStack(MekanismChemicals.HYDROGEN, 10)) == null,
              "the removed stick candidate must not survive the reload");
        marker("server-reload-verified.complete");
        LOGGER.info("MEKANISM_RECIPE_RUNTIME_SERVER phase=reload recipe_epoch=2 old_removed=true replacement_exposed=true");
    }

    private static void startFailingReload(MinecraftServer current) {
        try {
            Files.deleteIfExists(ROOT.resolve("fail-reload.trigger"));
            //The fixture-only reload listener sees the marker resource and fails the real resource reload deterministically.
            List<String> selected = new ArrayList<>(current.getPackRepository().getSelectedIds());
            current.reloadResources(selected).whenComplete((ignored, failure) -> current.execute(() -> {
                if (failure == null) throw new IllegalStateException("the deliberately failing reload unexpectedly succeeded");
                failedReloadProbe(current);
                marker("fail-reload.complete");
            }));
        } catch (RuntimeException | IOException failure) {
            throw new IllegalStateException("Unable to begin failing recipe reload", failure);
        }
    }

    private static void failedReloadProbe(MinecraftServer current) {
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        require(binding.epoch() == 2, "a failed reload must preserve the recipe context epoch");
        InputRecipeCache.ItemFluidChemical<FixtureRecipe> cache = new InputRecipeCache.ItemFluidChemical<>(RecipeCacheSource.forType(TYPE),
              FixtureRecipe::getItemInput, FixtureRecipe::getFluidInput, FixtureRecipe::getChemicalInput);
        require(cache.findFirstRecipe(current.overworld(), new ItemStack(Items.IRON_INGOT),
              new FluidStack(Fluids.WATER, 100), new ChemicalStack(MekanismChemicals.HYDROGEN, 10)) != null,
              "a failed reload must leave the previous usable cache in place");
        LOGGER.info("MEKANISM_RECIPE_RUNTIME_SERVER phase=failed_reload retained_epoch=2 cache_usable=true");
    }

    private static final class FailureReloadListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return FAILURE_LISTENER_ID;
        }

        @Override
        public void onResourceManagerReload(ResourceManager resources) {
            if (resources.getResource(FAILURE_MARKER).isPresent()) {
                LOGGER.info("MEKANISM_RECIPE_RUNTIME_SERVER phase=failed_reload_trigger marker_present=true");
                throw new IllegalStateException("Intentional recipe runtime fixture reload failure marker is present");
            }
        }
    }

    private static RecipeSerializer<FixtureRecipe> serializer() {
        RecipeSerializer<FixtureRecipe> local = serializer;
        if (local == null) {
            synchronized (RecipeRuntimeServerFixture.class) {
                local = serializer;
                if (local == null) {
                    local = new FixtureSerializer();
                    serializer = local;
                }
            }
        }
        return local;
    }

    private static void marker(String name) {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(ROOT.resolve(name), "ok\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to write recipe runtime marker " + name, failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** Disposable fixture recipe combining one item, one fluid, and one chemical input. */
    public static final class FixtureRecipe extends MekanismRecipe<RecipeInput> implements TriPredicate<ItemStack, FluidStack, ChemicalStack> {
        private final ItemStackIngredient itemInput;
        private final FluidStackIngredient fluidInput;
        private final ChemicalStackIngredient chemicalInput;

        public FixtureRecipe(ItemStackIngredient itemInput, FluidStackIngredient fluidInput, ChemicalStackIngredient chemicalInput) {
            this.itemInput = itemInput;
            this.fluidInput = fluidInput;
            this.chemicalInput = chemicalInput;
        }

        public ItemStackIngredient getItemInput() {
            return itemInput;
        }

        public FluidStackIngredient getFluidInput() {
            return fluidInput;
        }

        public ChemicalStackIngredient getChemicalInput() {
            return chemicalInput;
        }

        @Override
        public boolean test(ItemStack item, FluidStack fluid, ChemicalStack chemical) {
            return itemInput.test(item) && fluidInput.test(fluid) && chemicalInput.test(chemical);
        }

        @Override
        public boolean isIncomplete() {
            return itemInput.hasNoMatchingInstances() || fluidInput.hasNoMatchingInstances() || chemicalInput.hasNoMatchingInstances();
        }

        @Override
        public RecipeType<?> getType() {
            return TYPE;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return serializer();
        }

        @Override
        public boolean matches(RecipeInput input, Level level) {
            return false;
        }
    }

    private static final class FixtureSerializer implements RecipeSerializer<FixtureRecipe> {
        private final MapCodec<FixtureRecipe> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
              ItemStackIngredient.CODEC.fieldOf("item_input").forGetter(FixtureRecipe::getItemInput),
              FluidStackIngredient.CODEC.fieldOf("fluid_input").forGetter(FixtureRecipe::getFluidInput),
              ChemicalStackIngredient.CODEC.fieldOf("chemical_input").forGetter(FixtureRecipe::getChemicalInput)
        ).apply(instance, FixtureRecipe::new));
        private final StreamCodec<RegistryFriendlyByteBuf, FixtureRecipe> streamCodec = StreamCodec.composite(
              ItemStackIngredient.STREAM_CODEC, FixtureRecipe::getItemInput,
              FluidStackIngredient.STREAM_CODEC, FixtureRecipe::getFluidInput,
              ChemicalStackIngredient.STREAM_CODEC, FixtureRecipe::getChemicalInput,
              FixtureRecipe::new);

        @Override
        public MapCodec<FixtureRecipe> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FixtureRecipe> streamCodec() {
            return streamCodec;
        }
    }
}
