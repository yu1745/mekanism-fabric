package mekanism.common.recipe.lookup.cache;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.fluid.FluidStack;
import mekanism.api.functions.TriPredicate;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.registries.MekanismChemicals;
import mekanism.test.ChemicalTestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GenericInputCacheContextTest {
    private static RegistryAccess access;
    private static RecipeManager manager;

    @BeforeAll
    static void bootstrap() {
        access = ChemicalTestBootstrap.initialize();
        manager = new RecipeManager(access);
    }

    @AfterEach
    void clearBinding() {
        RecipeCacheContext.clear();
    }

    @Test
    void consumerRefreshIsOwnedByPublicationAndRaisedAgainByFullClear() {
        assertFalse(RecipeCacheContext.isInvalidationPending());
        RecipeCacheContext.Binding first = RecipeCacheContext.bind(manager, access, 1);
        assertTrue(RecipeCacheContext.isInvalidationPending());
        first.completeConsumerInvalidation();
        assertFalse(RecipeCacheContext.isInvalidationPending());
        first.advanceInvalidationGeneration();
        assertTrue(RecipeCacheContext.isInvalidationPending());
        RecipeCacheContext.bind(manager, access, 2);
        first.completeConsumerInvalidation();
        assertTrue(RecipeCacheContext.isInvalidationPending(), "finishing an obsolete publication cannot consume the new reload");
        RecipeCacheContext.clear();
        assertFalse(RecipeCacheContext.isInvalidationPending());
    }

    @Test
    void singleDoubleEitherAndTripleLookupsFindRecipes() {
        SingleItemRecipe single = new SingleItemRecipe(IngredientCreatorAccess.item().from(Items.IRON_INGOT, 2));
        DoubleItemChemicalRecipe doubleRecipe = new DoubleItemChemicalRecipe(IngredientCreatorAccess.item().from(Items.GOLD_INGOT, 1),
              IngredientCreatorAccess.chemicalStack().from(IngredientCreatorAccess.chemical().of(MekanismChemicals.HYDROGEN.get().getAsHolder()), 100));
        EitherSideChemicalRecipe either = new EitherSideChemicalRecipe(
              IngredientCreatorAccess.chemicalStack().from(IngredientCreatorAccess.chemical().of(MekanismChemicals.OXYGEN.get().getAsHolder()), 10),
              IngredientCreatorAccess.chemicalStack().from(IngredientCreatorAccess.chemical().of(MekanismChemicals.HYDROGEN.get().getAsHolder()), 10));
        TripleRecipe triple = new TripleRecipe(IngredientCreatorAccess.item().from(Items.DIAMOND, 1),
              IngredientCreatorAccess.fluid().fromHolder(Fluids.WATER.builtInRegistryHolder(), 1_000),
              IngredientCreatorAccess.chemicalStack().from(IngredientCreatorAccess.chemical().of(MekanismChemicals.STEAM.get().getAsHolder()), 5));
        replaceRecipes(holder("single", single), holder("double", doubleRecipe), holder("either", either), holder("triple", triple));
        RecipeCacheContext.bind(manager, 1);

        InputRecipeCache.SingleItem<SingleItemRecipe> singleCache = new InputRecipeCache.SingleItem<>(RecipeCacheSource.forType(SingleItemRecipe.TYPE), SingleItemRecipe::getInput);
        assertTrue(singleCache.containsInput(null, new ItemStack(Items.IRON_INGOT)));
        assertSame(single, singleCache.findFirstRecipe(null, new ItemStack(Items.IRON_INGOT, 5)));
        assertFalse(singleCache.containsInput(null, new ItemStack(Items.STICK)));
        assertNull(singleCache.findFirstRecipe(null, new ItemStack(Items.STICK, 5)));

        InputRecipeCache.ItemChemical<DoubleItemChemicalRecipe> doubleCache = new InputRecipeCache.ItemChemical<>(RecipeCacheSource.forType(DoubleItemChemicalRecipe.TYPE),
              DoubleItemChemicalRecipe::getItemInput, DoubleItemChemicalRecipe::getChemicalInput);
        assertTrue(doubleCache.containsInputA(null, new ItemStack(Items.GOLD_INGOT)));
        assertTrue(doubleCache.containsInputB(null, new ChemicalStack(MekanismChemicals.HYDROGEN, 10)));
        assertSame(doubleRecipe, doubleCache.findFirstRecipe(null, new ItemStack(Items.GOLD_INGOT, 2), new ChemicalStack(MekanismChemicals.HYDROGEN, 100)));
        assertTrue(doubleCache.containsInputAB(null, new ItemStack(Items.GOLD_INGOT, 2), new ChemicalStack(MekanismChemicals.HYDROGEN, 100)));
        assertTrue(doubleCache.containsInputAB(null, new ItemStack(Items.GOLD_INGOT, 2), ChemicalStack.EMPTY),
              "an empty B lookup with a present A is insertion-valid");
        assertFalse(doubleCache.containsInputAB(null, new ItemStack(Items.GOLD_INGOT, 2), new ChemicalStack(MekanismChemicals.OXYGEN, 100)),
              "a non-empty mismatched B is validated against the recipe");

        InputRecipeCache.EitherSideChemical<EitherSideChemicalRecipe> eitherCache = new InputRecipeCache.EitherSideChemical<>(RecipeCacheSource.forType(EitherSideChemicalRecipe.TYPE),
              EitherSideChemicalRecipe::getLeftInput, EitherSideChemicalRecipe::getRightInput);
        assertSame(either, eitherCache.findFirstRecipe(null, new ChemicalStack(MekanismChemicals.HYDROGEN, 20), new ChemicalStack(MekanismChemicals.OXYGEN, 20)));
        assertSame(either, eitherCache.findFirstRecipe(null, new ChemicalStack(MekanismChemicals.OXYGEN, 20), new ChemicalStack(MekanismChemicals.HYDROGEN, 20)),
              "either-side lookup succeeds in both directions");
        assertTrue(eitherCache.containsInput(null, new ChemicalStack(MekanismChemicals.HYDROGEN, 20)));

        InputRecipeCache.ItemFluidChemical<TripleRecipe> tripleCache = new InputRecipeCache.ItemFluidChemical<>(RecipeCacheSource.forType(TripleRecipe.TYPE),
              TripleRecipe::getItemInput, TripleRecipe::getFluidInput, TripleRecipe::getChemicalInput);
        assertSame(triple, tripleCache.findFirstRecipe(null, new ItemStack(Items.DIAMOND), new FluidStack(Fluids.WATER, 1_000), new ChemicalStack(MekanismChemicals.STEAM, 5)));
        assertTrue(tripleCache.containsInputABC(null, new ItemStack(Items.DIAMOND), new FluidStack(Fluids.WATER, 1_000), new ChemicalStack(MekanismChemicals.STEAM, 5)));
        assertTrue(tripleCache.containsInputBAC(null, new ItemStack(Items.DIAMOND), new FluidStack(Fluids.WATER, 1_000), new ChemicalStack(MekanismChemicals.STEAM, 5)));
        assertFalse(tripleCache.containsInputC(null, new ChemicalStack(MekanismChemicals.OXYGEN, 5)));
    }

    @Test
    void complexIngredientFallbackRoutesThroughRecipePredicates() {
        ItemStack named = new ItemStack(Items.IRON_INGOT);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Named"));
        SingleItemRecipe componentRecipe = new SingleItemRecipe(IngredientCreatorAccess.item().from(named));
        replaceRecipes(holder("component", componentRecipe));
        RecipeCacheContext.bind(manager, 1);
        InputRecipeCache.SingleItem<SingleItemRecipe> cache = new InputRecipeCache.SingleItem<>(RecipeCacheSource.forType(SingleItemRecipe.TYPE), SingleItemRecipe::getInput);
        //The component predicate has no quick per-item key, so the recipe is served through the complex fallback
        assertTrue(cache.containsInput(null, named));
        assertSame(componentRecipe, cache.findFirstRecipe(null, named));
        assertFalse(cache.containsInput(null, new ItemStack(Items.IRON_INGOT)),
              "a stack missing an expected component does not match, even through the fallback");
        assertFalse(cache.containsInput(null, new ItemStack(Items.STICK)));
        assertNull(cache.findFirstRecipe(null, new ItemStack(Items.STICK)));
        assertSame(componentRecipe, cache.findTypeBasedRecipe(null, named));
    }

    @Test
    void recipeOrderAndDedupArePreserved() {
        SingleItemRecipe first = new SingleItemRecipe(IngredientCreatorAccess.item().from(Items.OAK_PLANKS, 1));
        SingleItemRecipe second = new SingleItemRecipe(IngredientCreatorAccess.item().from(IngredientCreatorAccess.item().from(Items.OAK_PLANKS, 1).ingredient(), 2));
        SingleItemRecipe third = new SingleItemRecipe(IngredientCreatorAccess.item().from(Items.BIRCH_PLANKS, 1));
        replaceRecipes(holder("z_first", first), holder("a_second", second), holder("m_third", third));
        RecipeCacheContext.bind(manager, 1);
        InputRecipeCache.SingleItem<SingleItemRecipe> cache = new InputRecipeCache.SingleItem<>(RecipeCacheSource.forType(SingleItemRecipe.TYPE), SingleItemRecipe::getInput);
        //Both oak-plank recipes are candidates for the shared input, in manager order, and the first matching one wins
        List<RecipeHolder<SingleItemRecipe>> served = manager.getAllRecipesFor(SingleItemRecipe.TYPE);
        assertEquals(3, served.size());
        assertSame(first, cache.findFirstRecipe(null, new ItemStack(Items.OAK_PLANKS, 5)));
        //A chemical tag ingredient deduplicates holders shared by compound children
        mekanism.api.recipes.ingredients.chemical.ChemicalIngredient union = IngredientCreatorAccess.chemical()
              .compound(List.of(IngredientCreatorAccess.chemical().of(MekanismChemicals.HYDROGEN.get().getAsHolder()),
                    IngredientCreatorAccess.chemical().ofHolders(MekanismChemicals.HYDROGEN.get().getAsHolder(), MekanismChemicals.OXYGEN.get().getAsHolder())));
        assertEquals(2, IngredientCreatorAccess.chemicalStack().from(union, 10).ingredient().getChemicalHolders().size(),
              "compound children deduplicate to the distinct holders");
    }

    @Test
    void managerIdentityAndNullLevelUseTheBoundContext() {
        SingleItemRecipe single = new SingleItemRecipe(IngredientCreatorAccess.item().from(Items.COPPER_INGOT, 1));
        replaceRecipes(holder("single", single));
        //No bound context: no recipes are served
        InputRecipeCache.SingleItem<SingleItemRecipe> cache = new InputRecipeCache.SingleItem<>(RecipeCacheSource.forType(SingleItemRecipe.TYPE), SingleItemRecipe::getInput);
        assertFalse(cache.containsInput(null, new ItemStack(Items.COPPER_INGOT)));
        assertNull(cache.findFirstRecipe(null, new ItemStack(Items.COPPER_INGOT, 5)));
        //Null-level calls use the bound manager
        RecipeCacheContext.bind(manager, 1);
        assertTrue(cache.containsInput(null, new ItemStack(Items.COPPER_INGOT)));
        assertSame(single, cache.findFirstRecipe(null, new ItemStack(Items.COPPER_INGOT, 5)));
        assertEquals(1, RecipeCacheContext.currentBinding().epoch());
    }

    @Test
    void equalEpochFreshContextDiscardsCandidatesAtomically() {
        SingleItemRecipe before = new SingleItemRecipe(IngredientCreatorAccess.item().from(Items.COPPER_INGOT, 1));
        replaceRecipes(holder("before", before));
        RecipeCacheContext.bind(manager, 1);
        InputRecipeCache.SingleItem<SingleItemRecipe> cache = new InputRecipeCache.SingleItem<>(RecipeCacheSource.forType(SingleItemRecipe.TYPE), SingleItemRecipe::getInput);
        assertTrue(cache.containsInput(null, new ItemStack(Items.COPPER_INGOT)));
        SingleItemRecipe after = new SingleItemRecipe(IngredientCreatorAccess.item().from(Items.NETHERITE_INGOT, 1));
        replaceRecipes(holder("after", after));
        assertTrue(cache.containsInput(null, new ItemStack(Items.COPPER_INGOT)),
              "before any rebind the old candidates remain valid for the exact bound context");
        RecipeCacheContext.bind(manager, 1);
        assertFalse(cache.containsInput(null, new ItemStack(Items.COPPER_INGOT)), "an equal-epoch fresh context discards the old candidates atomically");
        assertTrue(cache.containsInput(null, new ItemStack(Items.NETHERITE_INGOT)));
        assertSame(after, cache.findFirstRecipe(null, new ItemStack(Items.NETHERITE_INGOT, 2)));
    }

    @Test
    void failedReloadRetainsContextWhileSuccessAndThreadsIsolate() throws InterruptedException {
        SingleItemRecipe first = new SingleItemRecipe(IngredientCreatorAccess.item().from(Items.COPPER_INGOT, 1));
        replaceRecipes(holder("first", first));
        RecipeCacheContext.bind(manager, 1);
        InputRecipeCache.SingleItem<SingleItemRecipe> cache = new InputRecipeCache.SingleItem<>(RecipeCacheSource.forType(SingleItemRecipe.TYPE), SingleItemRecipe::getInput);
        assertTrue(cache.containsInput(null, new ItemStack(Items.COPPER_INGOT)));
        SingleItemRecipe second = new SingleItemRecipe(IngredientCreatorAccess.item().from(Items.NETHERITE_INGOT, 1));
        replaceRecipes(holder("second", second));
        //A failed reload does not rebind: the previous context and its cache remain usable
        assertTrue(cache.containsInput(null, new ItemStack(Items.COPPER_INGOT)));
        assertSame(first, cache.findFirstRecipe(null, new ItemStack(Items.COPPER_INGOT, 2)));
        //A successful reload rebinds with an incremented epoch and the replacement becomes visible
        RecipeCacheContext.bind(manager, 2);
        assertTrue(cache.containsInput(null, new ItemStack(Items.NETHERITE_INGOT)));
        assertFalse(cache.containsInput(null, new ItemStack(Items.COPPER_INGOT)));
        //Another thread has no binding until it binds one itself
        Thread other = new Thread(() -> {
            InputRecipeCache.SingleItem<SingleItemRecipe> otherCache = new InputRecipeCache.SingleItem<>(RecipeCacheSource.forType(SingleItemRecipe.TYPE), SingleItemRecipe::getInput);
            assertFalse(otherCache.containsInput(null, new ItemStack(Items.NETHERITE_INGOT)), "a foreign thread without a binding serves no recipes");
            RecipeCacheContext.bind(manager, 1);
            assertTrue(otherCache.containsInput(null, new ItemStack(Items.NETHERITE_INGOT)));
        });
        other.start();
        other.join();
        assertTrue(cache.containsInput(null, new ItemStack(Items.NETHERITE_INGOT)), "the foreign thread's binding does not disturb this thread's context");
        //Clearing releases the manager and recipe references: no binding means no recipes
        RecipeCacheContext.clear();
        assertNull(RecipeCacheContext.currentBinding());
        assertFalse(cache.containsInput(null, new ItemStack(Items.NETHERITE_INGOT)));
    }

    private static void replaceRecipes(RecipeHolder<?>... holders) {
        manager.replaceRecipes(List.of(holders));
    }

    private static <RECIPE extends mekanism.api.recipes.MekanismRecipe<?>> RecipeHolder<RECIPE> holder(String path, RECIPE recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("mekanism_test", path), recipe);
    }

    private static final net.minecraft.world.item.crafting.RecipeSerializer<mekanism.api.recipes.MekanismRecipe<?>> FIXTURE_SERIALIZER
          = new net.minecraft.world.item.crafting.RecipeSerializer<>() {
        @Override
        public com.mojang.serialization.MapCodec<mekanism.api.recipes.MekanismRecipe<?>> codec() {
            throw new UnsupportedOperationException("Fixture serializer has no codec");
        }

        @Override
        public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, mekanism.api.recipes.MekanismRecipe<?>> streamCodec() {
            throw new UnsupportedOperationException("Fixture serializer has no stream codec");
        }
    };

    private static final class SingleItemRecipe extends MekanismRecipe<RecipeInput> implements Predicate<ItemStack> {
        private static final RecipeType<SingleItemRecipe> TYPE = new RecipeType<>() {
        };
        private final ItemStackIngredient input;

        private SingleItemRecipe(ItemStackIngredient input) {
            this.input = input;
        }

        ItemStackIngredient getInput() {
            return input;
        }

        @Override
        public boolean test(ItemStack stack) {
            return input.test(stack);
        }

        @Override
        public boolean isIncomplete() {
            return input.hasNoMatchingInstances();
        }

        @Override
        public boolean matches(RecipeInput input, net.minecraft.world.level.Level level) {
            return false;
        }

        @Override
        public RecipeType<?> getType() {
            return TYPE;
        }

        @Override
        public net.minecraft.world.item.crafting.RecipeSerializer<?> getSerializer() {
            return FIXTURE_SERIALIZER;
        }
    }

    private static final class DoubleItemChemicalRecipe extends MekanismRecipe<RecipeInput> implements BiPredicate<ItemStack, ChemicalStack> {
        private static final RecipeType<DoubleItemChemicalRecipe> TYPE = new RecipeType<>() {
        };
        private final ItemStackIngredient itemInput;
        private final ChemicalStackIngredient chemicalInput;

        private DoubleItemChemicalRecipe(ItemStackIngredient itemInput, ChemicalStackIngredient chemicalInput) {
            this.itemInput = itemInput;
            this.chemicalInput = chemicalInput;
        }

        ItemStackIngredient getItemInput() {
            return itemInput;
        }

        ChemicalStackIngredient getChemicalInput() {
            return chemicalInput;
        }

        @Override
        public boolean test(ItemStack stack, ChemicalStack chemical) {
            return itemInput.test(stack) && chemicalInput.test(chemical);
        }

        @Override
        public boolean isIncomplete() {
            return itemInput.hasNoMatchingInstances() || chemicalInput.hasNoMatchingInstances();
        }

        @Override
        public boolean matches(RecipeInput input, net.minecraft.world.level.Level level) {
            return false;
        }

        @Override
        public RecipeType<?> getType() {
            return TYPE;
        }

        @Override
        public net.minecraft.world.item.crafting.RecipeSerializer<?> getSerializer() {
            return FIXTURE_SERIALIZER;
        }
    }

    private static final class EitherSideChemicalRecipe extends MekanismRecipe<RecipeInput> implements BiPredicate<ChemicalStack, ChemicalStack> {
        private static final RecipeType<EitherSideChemicalRecipe> TYPE = new RecipeType<>() {
        };
        private final ChemicalStackIngredient leftInput;
        private final ChemicalStackIngredient rightInput;

        private EitherSideChemicalRecipe(ChemicalStackIngredient leftInput, ChemicalStackIngredient rightInput) {
            this.leftInput = leftInput;
            this.rightInput = rightInput;
        }

        ChemicalStackIngredient getLeftInput() {
            return leftInput;
        }

        ChemicalStackIngredient getRightInput() {
            return rightInput;
        }

        @Override
        public boolean test(ChemicalStack left, ChemicalStack right) {
            return leftInput.test(left) && rightInput.test(right) || rightInput.test(left) && leftInput.test(right);
        }

        @Override
        public boolean isIncomplete() {
            return leftInput.hasNoMatchingInstances() || rightInput.hasNoMatchingInstances();
        }

        @Override
        public boolean matches(RecipeInput input, net.minecraft.world.level.Level level) {
            return false;
        }

        @Override
        public RecipeType<?> getType() {
            return TYPE;
        }

        @Override
        public net.minecraft.world.item.crafting.RecipeSerializer<?> getSerializer() {
            return FIXTURE_SERIALIZER;
        }
    }

    private static final class TripleRecipe extends MekanismRecipe<RecipeInput> implements TriPredicate<ItemStack, FluidStack, ChemicalStack> {
        private static final RecipeType<TripleRecipe> TYPE = new RecipeType<>() {
        };
        private final ItemStackIngredient itemInput;
        private final FluidStackIngredient fluidInput;
        private final ChemicalStackIngredient chemicalInput;

        private TripleRecipe(ItemStackIngredient itemInput, FluidStackIngredient fluidInput, ChemicalStackIngredient chemicalInput) {
            this.itemInput = itemInput;
            this.fluidInput = fluidInput;
            this.chemicalInput = chemicalInput;
        }

        ItemStackIngredient getItemInput() {
            return itemInput;
        }

        FluidStackIngredient getFluidInput() {
            return fluidInput;
        }

        ChemicalStackIngredient getChemicalInput() {
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
        public boolean matches(RecipeInput input, net.minecraft.world.level.Level level) {
            return false;
        }

        @Override
        public RecipeType<?> getType() {
            return TYPE;
        }

        @Override
        public net.minecraft.world.item.crafting.RecipeSerializer<?> getSerializer() {
            return FIXTURE_SERIALIZER;
        }
    }
}
