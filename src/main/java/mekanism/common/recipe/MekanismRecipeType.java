package mekanism.common.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import mekanism.api.recipes.ChemicalChemicalToChemicalRecipe;
import mekanism.api.recipes.ChemicalCrystallizerRecipe;
import mekanism.api.recipes.ChemicalDissolutionRecipe;
import mekanism.api.recipes.ChemicalToChemicalRecipe;
import mekanism.api.recipes.CombinerRecipe;
import mekanism.api.recipes.ElectrolysisRecipe;
import mekanism.api.recipes.FluidChemicalToChemicalRecipe;
import mekanism.api.recipes.FluidToFluidRecipe;
import mekanism.api.recipes.ItemStackChemicalToItemStackRecipe;
import mekanism.api.recipes.ItemStackToChemicalRecipe;
import mekanism.api.recipes.ItemStackToEnergyRecipe;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.NucleosynthesizingRecipe;
import mekanism.api.recipes.PressurizedReactionRecipe;
import mekanism.api.recipes.RotaryRecipe;
import mekanism.api.recipes.SawmillRecipe;
import mekanism.api.recipes.basic.BasicSmeltingRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.recipes.vanilla_input.BiChemicalRecipeInput;
import mekanism.api.recipes.vanilla_input.ReactionRecipeInput;
import mekanism.api.recipes.vanilla_input.RotaryRecipeInput;
import mekanism.api.recipes.vanilla_input.SingleChemicalRecipeInput;
import mekanism.api.recipes.vanilla_input.SingleFluidChemicalRecipeInput;
import mekanism.api.recipes.vanilla_input.SingleFluidRecipeInput;
import mekanism.api.recipes.vanilla_input.SingleItemChemicalRecipeInput;
import mekanism.common.recipe.lookup.cache.IInputRecipeCache;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.DoubleItem;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.EitherSideChemical;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.FluidChemical;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.ItemChemical;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.ItemFluidChemical;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.SingleChemical;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.SingleFluid;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.SingleItem;
import mekanism.common.recipe.lookup.cache.RecipeCacheContext;
import mekanism.common.recipe.lookup.cache.RecipeCacheSource;
import mekanism.common.recipe.lookup.cache.RotaryInputRecipeCache;
import mekanism.common.registration.impl.RecipeTypeDeferredRegister;
import mekanism.common.registration.impl.RecipeTypeRegistryObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one canonical owner of every Mekanism recipe type.
 *
 * <p>Each concrete {@link RecipeType} is created exactly once by {@link RecipeTypeDeferredRegister#registerMek} before any registry write, and the same instance is
 * what the registry ends up holding, so {@code Recipe#getType()} identity and vanilla {@code RecipeManager}'s identity-keyed {@code byType} grouping survive
 * registration unchanged. This class is also the recipe source for its own input caches, so the caches share this owner's own-recipes-first ordering, incomplete
 * filtering and synthetic vanilla smelting instead of bypassing them.</p>
 *
 * <p>Recipe lists and registry access live only inside the thread-bound {@link RecipeCacheContext.Binding}. There is no process-global recipe list and no global
 * client/server world fallback: a missing context serves nothing, and an explicit manager/registry-access query always answers for the identities it was handed.</p>
 *
 * <p>The 26 declaration ids are written as local literals rather than read from the API declaration class, so neither load order can force the other through a static
 * initializer cycle.</p>
 */
public class MekanismRecipeType<VANILLA_INPUT extends RecipeInput, RECIPE extends MekanismRecipe<VANILLA_INPUT>, INPUT_CACHE extends IInputRecipeCache>
      implements RecipeType<RECIPE>, IMekanismRecipeTypeProvider<VANILLA_INPUT, RECIPE, INPUT_CACHE>, RecipeCacheSource<RECIPE> {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/MekanismRecipeType");
    private static final String MODID = "mekanism";
    private static final ResourceLocation SMELTING_ID = ResourceLocation.fromNamespaceAndPath(MODID, "smelting");

    public static final RecipeTypeDeferredRegister RECIPE_TYPES = new RecipeTypeDeferredRegister(MODID);

    public static final RecipeTypeRegistryObject<SingleRecipeInput, ItemStackToItemStackRecipe, SingleItem<ItemStackToItemStackRecipe>> CRUSHING = register("crushing", recipeType -> new SingleItem<>(recipeType, ItemStackToItemStackRecipe::getInput));

    public static final RecipeTypeRegistryObject<SingleRecipeInput, ItemStackToItemStackRecipe, SingleItem<ItemStackToItemStackRecipe>> ENRICHING = register("enriching", recipeType -> new SingleItem<>(recipeType, ItemStackToItemStackRecipe::getInput));

    public static final RecipeTypeRegistryObject<SingleRecipeInput, ItemStackToItemStackRecipe, SingleItem<ItemStackToItemStackRecipe>> SMELTING = register("smelting", recipeType -> new SingleItem<>(recipeType, ItemStackToItemStackRecipe::getInput));

    public static final RecipeTypeRegistryObject<BiChemicalRecipeInput, ChemicalChemicalToChemicalRecipe, EitherSideChemical<ChemicalChemicalToChemicalRecipe>> CHEMICAL_INFUSING = register("chemical_infusing", recipeType -> new EitherSideChemical<>(recipeType, ChemicalChemicalToChemicalRecipe::getLeftInput, ChemicalChemicalToChemicalRecipe::getRightInput));

    public static final RecipeTypeRegistryObject<RecipeInput, CombinerRecipe, DoubleItem<CombinerRecipe>> COMBINING = register("combining", recipeType -> new DoubleItem<>(recipeType, CombinerRecipe::getMainInput, CombinerRecipe::getExtraInput));

    public static final RecipeTypeRegistryObject<SingleFluidRecipeInput, ElectrolysisRecipe, SingleFluid<ElectrolysisRecipe>> SEPARATING = register("separating", recipeType -> new SingleFluid<>(recipeType, ElectrolysisRecipe::getInput));

    public static final RecipeTypeRegistryObject<SingleFluidChemicalRecipeInput, FluidChemicalToChemicalRecipe, FluidChemical<FluidChemicalToChemicalRecipe>> WASHING = register("washing", recipeType -> new FluidChemical<>(recipeType, FluidChemicalToChemicalRecipe::getFluidInput, FluidChemicalToChemicalRecipe::getChemicalInput));

    public static final RecipeTypeRegistryObject<SingleFluidRecipeInput, FluidToFluidRecipe, SingleFluid<FluidToFluidRecipe>> EVAPORATING = register("evaporating", recipeType -> new SingleFluid<>(recipeType, FluidToFluidRecipe::getInput));

    public static final RecipeTypeRegistryObject<SingleChemicalRecipeInput, ChemicalToChemicalRecipe, SingleChemical<ChemicalToChemicalRecipe>> ACTIVATING = register("activating", recipeType -> new SingleChemical<>(recipeType, ChemicalToChemicalRecipe::getInput));

    public static final RecipeTypeRegistryObject<SingleChemicalRecipeInput, ChemicalToChemicalRecipe, SingleChemical<ChemicalToChemicalRecipe>> CENTRIFUGING = register("centrifuging", recipeType -> new SingleChemical<>(recipeType, ChemicalToChemicalRecipe::getInput));

    public static final RecipeTypeRegistryObject<SingleChemicalRecipeInput, ChemicalCrystallizerRecipe, SingleChemical<ChemicalCrystallizerRecipe>> CRYSTALLIZING = register("crystallizing", recipeType -> new SingleChemical<>(recipeType, ChemicalCrystallizerRecipe::getInput));

    public static final RecipeTypeRegistryObject<SingleItemChemicalRecipeInput, ChemicalDissolutionRecipe, ItemChemical<ChemicalDissolutionRecipe>> DISSOLUTION = register("dissolution", recipeType -> new ItemChemical<>(recipeType, ChemicalDissolutionRecipe::getItemInput, ChemicalDissolutionRecipe::getChemicalInput));

    public static final RecipeTypeRegistryObject<SingleItemChemicalRecipeInput, ItemStackChemicalToItemStackRecipe, ItemChemical<ItemStackChemicalToItemStackRecipe>> COMPRESSING = register("compressing", recipeType -> new ItemChemical<>(recipeType, ItemStackChemicalToItemStackRecipe::getItemInput, ItemStackChemicalToItemStackRecipe::getChemicalInput));

    public static final RecipeTypeRegistryObject<SingleItemChemicalRecipeInput, ItemStackChemicalToItemStackRecipe, ItemChemical<ItemStackChemicalToItemStackRecipe>> PURIFYING = register("purifying", recipeType -> new ItemChemical<>(recipeType, ItemStackChemicalToItemStackRecipe::getItemInput, ItemStackChemicalToItemStackRecipe::getChemicalInput));

    public static final RecipeTypeRegistryObject<SingleItemChemicalRecipeInput, ItemStackChemicalToItemStackRecipe, ItemChemical<ItemStackChemicalToItemStackRecipe>> INJECTING = register("injecting", recipeType -> new ItemChemical<>(recipeType, ItemStackChemicalToItemStackRecipe::getItemInput, ItemStackChemicalToItemStackRecipe::getChemicalInput));

    public static final RecipeTypeRegistryObject<SingleItemChemicalRecipeInput, NucleosynthesizingRecipe, ItemChemical<NucleosynthesizingRecipe>> NUCLEOSYNTHESIZING = register("nucleosynthesizing", recipeType -> new ItemChemical<>(recipeType, NucleosynthesizingRecipe::getItemInput, NucleosynthesizingRecipe::getChemicalInput));

    public static final RecipeTypeRegistryObject<SingleRecipeInput, ItemStackToEnergyRecipe, SingleItem<ItemStackToEnergyRecipe>> ENERGY_CONVERSION = register("energy_conversion", recipeType -> new SingleItem<>(recipeType, ItemStackToEnergyRecipe::getInput));

    public static final RecipeTypeRegistryObject<SingleRecipeInput, ItemStackToChemicalRecipe, SingleItem<ItemStackToChemicalRecipe>> CHEMICAL_CONVERSION = register("chemical_conversion", recipeType -> new SingleItem<>(recipeType, ItemStackToChemicalRecipe::getInput));

    public static final RecipeTypeRegistryObject<SingleRecipeInput, ItemStackToChemicalRecipe, SingleItem<ItemStackToChemicalRecipe>> OXIDIZING = register("oxidizing", recipeType -> new SingleItem<>(recipeType, ItemStackToChemicalRecipe::getInput));

    public static final RecipeTypeRegistryObject<SingleRecipeInput, ItemStackToChemicalRecipe, SingleItem<ItemStackToChemicalRecipe>> PIGMENT_EXTRACTING = register("pigment_extracting", recipeType -> new SingleItem<>(recipeType, ItemStackToChemicalRecipe::getInput));

    public static final RecipeTypeRegistryObject<BiChemicalRecipeInput, ChemicalChemicalToChemicalRecipe, EitherSideChemical<ChemicalChemicalToChemicalRecipe>> PIGMENT_MIXING = register("pigment_mixing", recipeType -> new EitherSideChemical<>(recipeType, ChemicalChemicalToChemicalRecipe::getLeftInput, ChemicalChemicalToChemicalRecipe::getRightInput));

    public static final RecipeTypeRegistryObject<SingleItemChemicalRecipeInput, ItemStackChemicalToItemStackRecipe, ItemChemical<ItemStackChemicalToItemStackRecipe>> METALLURGIC_INFUSING = register("metallurgic_infusing", recipeType -> new ItemChemical<>(recipeType, ItemStackChemicalToItemStackRecipe::getItemInput, ItemStackChemicalToItemStackRecipe::getChemicalInput));

    public static final RecipeTypeRegistryObject<SingleItemChemicalRecipeInput, ItemStackChemicalToItemStackRecipe, ItemChemical<ItemStackChemicalToItemStackRecipe>> PAINTING = register("painting", recipeType -> new ItemChemical<>(recipeType, ItemStackChemicalToItemStackRecipe::getItemInput, ItemStackChemicalToItemStackRecipe::getChemicalInput));

    public static final RecipeTypeRegistryObject<ReactionRecipeInput, PressurizedReactionRecipe, ItemFluidChemical<PressurizedReactionRecipe>> REACTION = register("reaction", recipeType -> new ItemFluidChemical<>(recipeType, PressurizedReactionRecipe::getInputSolid, PressurizedReactionRecipe::getInputFluid, PressurizedReactionRecipe::getInputChemical));

    public static final RecipeTypeRegistryObject<RotaryRecipeInput, RotaryRecipe, RotaryInputRecipeCache> ROTARY = register("rotary", RotaryInputRecipeCache::new);

    public static final RecipeTypeRegistryObject<SingleRecipeInput, SawmillRecipe, SingleItem<SawmillRecipe>> SAWING = register("sawing", recipeType -> new SingleItem<>(recipeType, SawmillRecipe::getInput));

    private static <VANILLA_INPUT extends RecipeInput, RECIPE extends MekanismRecipe<VANILLA_INPUT>, INPUT_CACHE extends IInputRecipeCache>
    RecipeTypeRegistryObject<VANILLA_INPUT, RECIPE, INPUT_CACHE> register(
          String path,
          Function<MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE>, INPUT_CACHE> inputCacheCreator
    ) {
        //Note: The single instance is created here, before any registry write, and handed to the deferred register as its prebuilt value.
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MODID, path);
        return RECIPE_TYPES.registerMek(path, new MekanismRecipeType<>(id, inputCacheCreator));
    }

    /**
     * Clears this owner's cached state from the calling thread's current binding: every recipe list and every input-cache index of all 26 types.
     *
     * <p>The current binding additionally advances its cache-invalidation generation exactly once, so monitoring holders stop processing candidates that were resolved
     * before the clear. The publication identity and epoch are deliberately left alone: an explicit clear discards derived state, it is not a successful datapack
     * reload.</p>
     */
    public static void clearCache() {
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        if (binding != null) {
            binding.advanceInvalidationGeneration();
        }
        for (RecipeTypeRegistryObject<?, ?, ?> entry : RECIPE_TYPES.getEntries()) {
            entry.getRecipeType().clearCaches();
        }
    }

    /**
     * Checks every recipe type for incomplete recipes using the given server's manager and registry access.
     *
     * @return {@code true} if at least one incomplete recipe was reported.
     */
    public static boolean checkIncompleteRecipes(MinecraftServer server) {
        return checkIncompleteRecipes(server.getRecipeManager(), server.registryAccess());
    }

    /**
     * Checks every recipe type for incomplete recipes using the given manager and registry access.
     *
     * @return {@code true} if at least one incomplete recipe was reported.
     */
    public static boolean checkIncompleteRecipes(RecipeManager recipeManager, RegistryAccess registryAccess) {
        boolean foundIncompleteRecipes = false;
        for (RecipeTypeRegistryObject<?, ?, ?> entry : RECIPE_TYPES.getEntries()) {
            foundIncompleteRecipes |= entry.getRecipeType().checkMyIncompleteRecipes(recipeManager, registryAccess);
        }
        return foundIncompleteRecipes;
    }

    private final ResourceLocation registryName;
    private final INPUT_CACHE inputCache;

    private MekanismRecipeType(ResourceLocation name, Function<MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE>, INPUT_CACHE> inputCacheCreator) {
        this.registryName = name;
        //Note: The input cache captures this owner as its recipe source; nothing is evaluated at construction time, so initialization cannot recurse.
        this.inputCache = inputCacheCreator.apply(this);
    }

    @Override
    public String toString() {
        return registryName.toString();
    }

    @Override
    public ResourceLocation getRegistryName() {
        return registryName;
    }

    @Override
    public MekanismRecipeType<VANILLA_INPUT, RECIPE, INPUT_CACHE> getRecipeType() {
        return this;
    }

    private void clearCaches() {
        inputCache.clear();
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        if (binding != null) {
            binding.clearRecipes(this);
        }
    }

    @Override
    public INPUT_CACHE getInputCache() {
        return inputCache;
    }

    /**
     * Level-based lookup, obeying the bound recipe-cache contract: no bound context serves nothing, a non-null level whose manager is not the exact bound manager
     * fails, and a null level uses the bound manager and the registry access published with it.
     */
    @NotNull
    @Override
    public List<RecipeHolder<RECIPE>> getRecipes(@Nullable Level world) {
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        if (binding == null) {
            //No published recipe context on this thread: never consult a stale process-global world
            return List.of();
        }
        if (world != null && world.getRecipeManager() != binding.manager()) {
            throw new IllegalStateException("Recipe lookup level is not bound to the active recipe manager (epoch " + binding.epoch() + ")");
        }
        return getRecipes(binding.manager(), binding.registryAccess());
    }

    /**
     * Manager-only lookup. Registry access is borrowed only from a binding that serves this exact manager; without one the owner's own recipes are served and
     * synthetic vanilla smelting is omitted, exactly as a null-access query specifies.
     */
    @NotNull
    @Override
    public List<RecipeHolder<RECIPE>> getRecipes(@NotNull RecipeManager recipeManager) {
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        RegistryAccess registryAccess = binding != null && binding.manager() == recipeManager ? binding.registryAccess() : null;
        return getRecipes(recipeManager, registryAccess);
    }

    /**
     * Explicit lookup. This always answers for the supplied manager and registry access; the current binding's cached list is reused only when this binding serves
     * both of those exact identities, so a foreign query is computed uncached rather than rejected or allowed to pollute current state.
     */
    @NotNull
    @Override
    public List<RecipeHolder<RECIPE>> getRecipes(@NotNull RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        if (binding != null && binding.manager() == recipeManager && binding.registryAccess() == registryAccess) {
            //Note: An empty result is cached too, and the stored list is immutable
            return binding.recipes(this, () -> usable(getRecipesUncached(recipeManager, registryAccess)));
        }
        return usable(getRecipesUncached(recipeManager, registryAccess));
    }

    /**
     * This owner as the input caches' recipe source: own recipes first, incomplete recipes filtered, plus synthetic vanilla smelting for smelting when access is known.
     */
    @NotNull
    @Override
    public List<RecipeHolder<RECIPE>> recipes(@NotNull RecipeManager recipeManager) {
        return getRecipes(recipeManager);
    }

    @Override
    public List<RecipeHolder<RECIPE>> recipes(@NotNull RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        return getRecipes(recipeManager, registryAccess);
    }

    @NotNull
    private static <RECIPE extends MekanismRecipe<?>> List<RecipeHolder<RECIPE>> usable(List<RecipeHolder<RECIPE>> recipes) {
        //Note: This is a fresh immutable list; incomplete recipes are dropped because a partially complete recipe must never be looked up
        return recipes.stream()
              .filter(recipe -> !recipe.value().isIncomplete())
              .toList();
    }

    /**
     * Gets the recipes directly from the manager, adding the synthetic vanilla smelting recipes for the smelting type when registry access is available.
     *
     * @param recipeManager  The manager to query.
     * @param registryAccess Registry access to use, or {@code null} to serve only this owner's own smelting recipes.
     */
    @NotNull
    private List<RecipeHolder<RECIPE>> getRecipesUncached(@NotNull RecipeManager recipeManager, @Nullable RegistryAccess registryAccess) {
        //Note: This is a fresh mutable list that gets returned
        List<RecipeHolder<RECIPE>> recipes = recipeManager.getAllRecipesFor(this);
        if (SMELTING_ID.equals(registryName)) {
            if (registryAccess == null) {
                //Without registry access the vanilla smelting result cannot be resolved, so only the owner's own recipes are served
                return recipes;
            }
            //Ensure the recipes can be modified
            recipes = new ArrayList<>(recipes);
            for (RecipeHolder<SmeltingRecipe> smeltingRecipe : recipeManager.getAllRecipesFor(RecipeType.SMELTING)) {
                ItemStack recipeOutput = smeltingRecipe.value().getResultItem(registryAccess);
                if (!smeltingRecipe.value().isSpecial() && !smeltingRecipe.value().isIncomplete() && !recipeOutput.isEmpty()) {
                    NonNullList<Ingredient> ingredients = smeltingRecipe.value().getIngredients();
                    if (ingredients.isEmpty()) {
                        //Something went wrong
                        continue;
                    }
                    //Note: The audited compound union preserves the official CompoundIngredient semantics, and the synthetic id keeps the original namespace
                    ItemStackIngredient input = ItemStackIngredient.of(ingredients, 1);
                    recipes.add(new RecipeHolder<>(smeltingRecipe.id().withPrefix("/mekanism_generated/"),
                          castRecipe(new BasicSmeltingRecipe(input, recipeOutput))));
                }
            }
        }
        return recipes;
    }

    @SuppressWarnings("unchecked")
    private RECIPE castRecipe(MekanismRecipe<?> o) {
        if (o.getType() != this) {
            throw new IllegalArgumentException("Wrong recipe type");
        }
        return (RECIPE) o;
    }

    private boolean checkMyIncompleteRecipes(RecipeManager recipeManager, RegistryAccess registryAccess) {
        boolean incomplete = false;
        for (RecipeHolder<RECIPE> holder : getRecipesUncached(recipeManager, registryAccess)) {
            if (!holder.value().isIncomplete()) {
                continue;
            }
            LOGGER.error("Incomplete recipe detected: {}", holder.id());
            incomplete = true;
            holder.value().logMissingTags();
        }
        return incomplete;
    }

    /**
     * Helper for getting a recipe from a world's recipe manager.
     */
    public static <I extends RecipeInput, RECIPE_TYPE extends Recipe<I>> Optional<RecipeHolder<RECIPE_TYPE>> getRecipeFor(RecipeType<RECIPE_TYPE> recipeType, I input,
          Level level) {
        //Only allow looking up complete recipes or special recipes as we only use this method for vanilla recipe types
        // and special recipes return that they are not complete
        return level.getRecipeManager().getRecipeFor(recipeType, input, level)
              .filter(recipe -> recipe.value().isSpecial() || !recipe.value().isIncomplete());
    }

    /**
     * Helper for getting a recipe from a world's recipe manager.
     */
    public static Optional<RecipeHolder<?>> byKey(Level level, ResourceLocation id) {
        //Only allow looking up complete recipes or special recipes as we only use this method for vanilla recipe types
        // and special recipes return that they are not complete
        return level.getRecipeManager().byKey(id)
              .filter(recipe -> recipe.value().isSpecial() || !recipe.value().isIncomplete());
    }
}
