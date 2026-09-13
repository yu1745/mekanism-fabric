package mekanism.common.recipe.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.FluidStack;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.ChemicalChemicalToChemicalRecipe;
import mekanism.api.recipes.ChemicalDissolutionRecipe;
import mekanism.api.recipes.ChemicalToChemicalRecipe;
import mekanism.api.recipes.CombinerRecipe;
import mekanism.api.recipes.FluidChemicalToChemicalRecipe;
import mekanism.api.recipes.FluidToFluidRecipe;
import mekanism.api.recipes.ItemStackChemicalToItemStackRecipe;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.PressurizedReactionRecipe;
import mekanism.api.recipes.basic.BasicChemicalDissolutionRecipe;
import mekanism.api.recipes.basic.BasicCompressingRecipe;
import mekanism.api.recipes.basic.BasicCrushingRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.cache.ItemStackConstantChemicalToObjectCachedRecipe;
import mekanism.api.recipes.cache.ItemStackConstantChemicalToObjectCachedRecipe.ChemicalUsageMultiplier;
import mekanism.api.recipes.cache.OneInputCachedRecipe;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.inputs.ILongInputHandler;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.DoubleItem;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.EitherSideChemical;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.FluidChemical;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.ItemChemical;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.ItemFluidChemical;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.SingleChemical;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.SingleFluid;
import mekanism.common.recipe.lookup.cache.InputRecipeCache.SingleItem;
import mekanism.common.recipe.lookup.cache.RecipeCacheContext;
import mekanism.common.recipe.lookup.monitor.FactoryRecipeCacheLookupMonitor;
import mekanism.common.recipe.lookup.monitor.RecipeCacheLookupMonitor;
import mekanism.common.registries.MekanismChemicals;
import mekanism.test.ChemicalTestBootstrap;
import mekanism.test.RecipeLevelStub;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Focused contracts for the generic recipe lookup monitors and their handler/ownership surface.
 *
 * <p>Every case drives the real production monitor, the real canonical owner/provider and the real input caches. Lower-level arithmetic, direction and ingredient
 * matrices stay owned by {@code GenericInputCacheContextTest}, {@code RotaryInputCacheContextTest} and {@code InputHelperExecutionTest}; they are deliberately not
 * repeated here.</p>
 */
class RecipeCacheLookupMonitorTest {

    private static RegistryAccess access;
    private static RecipeManager manager;

    @BeforeAll
    static void bootstrap() {
        access = ChemicalTestBootstrap.initialize();
        MekanismChemicalIngredientTypesRegistration.ensure();
        manager = new RecipeManager(access);
    }

    @AfterEach
    void clearBinding() {
        RecipeCacheContext.clear();
    }

    @Test
    void handlerRoutesThroughCanonicalCacheAndMonitorResolvesWrongIndexAndSavedTicks() {
        BasicCrushingRecipe crushing = crushing(Items.IRON_INGOT, new ItemStack(Items.IRON_NUGGET, 9));
        replace(crushing);
        RecipeCacheContext.bind(manager, access, 1);

        InstantItemHandler handler = new InstantItemHandler();
        RecipeCacheLookupMonitor<ItemStackToItemStackRecipe> monitor = new RecipeCacheLookupMonitor<>(handler, 0);
        assertSame(MekanismRecipeType.CRUSHING, handler.getRecipeType());
        assertSame(MekanismRecipeType.CRUSHING.getInputCache(), handler.getRecipeType().getInputCache());
        //The handler helper routes through the canonical owner's real input cache
        assertTrue(handler.containsRecipe(new ItemStack(Items.IRON_INGOT)));
        assertSame(crushing, handler.findFirstRecipe(new ItemStack(Items.IRON_INGOT, 4)));
        assertFalse(handler.containsRecipe(new ItemStack(Items.DIAMOND)));

        //Saved operating ticks reach the fresh cached recipe through the monitor's cacheIndex matching
        handler.savedTicks = 3;
        assertTrue(monitor.updateAndProcess());
        assertNotNull(monitor.getCachedRecipe(0));
        assertEquals(3, handler.probe.loadedOperatingTicks, "the saved operating tick count must be loaded into the fresh cached recipe");
        assertSame(handler.cached.getRecipe(), crushing);

        //A wrong cacheIndex is not this monitor's cache: it neither reads nor writes its state
        assertNull(monitor.getCachedRecipe(1));
        assertSame(0, monitor.getSavedOperatingTicks(1));
        assertNull(monitor.getRecipe(1));
        assertNull(monitor.createNewCachedRecipe(crushing, 1));
        assertFalse(monitor.hasNoRecipe(1));
        monitor.setHasNoRecipe(1);
        assertFalse(monitor.hasNoRecipe(1), "a foreign cacheIndex must not mark this monitor as having no recipe");
    }

    @Test
    void noRecipeMemoizationCallbackOrderingAndUnpauseHappenInOrder() {
        replace();
        RecipeCacheContext.bind(manager, access, 1);
        InstantItemHandler handler = new InstantItemHandler();
        RecipeCacheLookupMonitor<ItemStackToItemStackRecipe> monitor = new RecipeCacheLookupMonitor<>(handler, 0);

        RecipeCacheLookupMonitor<ItemStackToItemStackRecipe> finalMonitor = monitor;
        //The monitor's own onChange is not part of the handler callback, so subscribe through the public hook instead
        List<String> order = new ArrayList<>();
        handler.onContentsChangedHook = () -> order.add("handler");
        RecipeCacheLookupMonitor<ItemStackToItemStackRecipe> orderingMonitor = new RecipeCacheLookupMonitor<>(handler, 0) {
            @Override
            public void onChange() {
                order.add("change");
                super.onChange();
            }
        };
        orderingMonitor.onContentsChanged();
        assertEquals(List.of("handler", "change"), order, "the handler contents callback must precede the monitor's onChange");

        //No matching recipe: the miss is memoized and the following lookup is short circuited
        assertFalse(monitor.updateAndProcess());
        assertEquals(1, handler.recipeLookups, "the first lookup consults the handler");
        assertTrue(finalMonitor.hasNoRecipe(0), "a missing recipe must be memoized");
        assertFalse(monitor.updateAndProcess());
        assertEquals(1, handler.recipeLookups, "the memoized miss must short circuit the next lookup");

        //A rebind invalidates even with unchanged inputs, so the handler is consulted again
        BasicCrushingRecipe crushing = crushing(Items.IRON_INGOT, new ItemStack(Items.IRON_NUGGET, 9));
        replace(crushing);
        RecipeCacheContext.bind(manager, access, 1);
        assertTrue(finalMonitor.updateAndProcess(), "a rebind must re-resolve even when the inputs did not change");
        assertEquals(2, handler.recipeLookups);
        assertSame(crushing, handler.cached.getRecipe());
        assertEquals(1, handler.recipeChanges, "the newly resolved cache is reported exactly once");

        //A still-valid cache is retained without another handler lookup, and unpause is delivered once to the cached recipe
        handler.probe.unpauseCalls = 0;
        finalMonitor.unpause();
        assertTrue(finalMonitor.updateAndProcess());
        assertEquals(2, handler.recipeLookups, "a still-valid cache must not re-consult the handler");
        assertEquals(1, handler.probe.unpauseCalls, "unpause must be forwarded exactly once");
        assertTrue(finalMonitor.updateAndProcess());
        assertEquals(1, handler.probe.unpauseCalls, "the unpause flag must be consumed by the first processing tick");
    }

    @Test
    void explicitClearAndRebindInvalidateWithoutConsumingAndKeepPublicationIdentity() {
        BasicCrushingRecipe first = crushing(Items.IRON_INGOT, new ItemStack(Items.IRON_NUGGET, 9));
        replace(first);
        RecipeCacheContext.Binding binding = RecipeCacheContext.bind(manager, access, 1);
        Object token = binding.identityToken();
        InstantItemHandler handler = new InstantItemHandler();
        RecipeCacheLookupMonitor<ItemStackToItemStackRecipe> monitor = new RecipeCacheLookupMonitor<>(handler, 0);
        assertTrue(monitor.updateAndProcess());
        assertSame(first, handler.cached.getRecipe());
        assertFalse(monitor.invalidateCache(), "a just-resolved binding is current");

        //The predicate is not an acknowledgement: checking it twice must not swallow the invalidation
        BasicCrushingRecipe second = crushing(Items.DIAMOND, new ItemStack(Items.DIAMOND_BLOCK, 1));
        replace(second);
        //Change the real input too: the clear must make the monitor re-resolve rather than reuse the old candidate
        handler.slot.stack = new ItemStack(Items.DIAMOND, 4);
        MekanismRecipeType.clearCache();
        assertTrue(monitor.invalidateCache());
        assertTrue(monitor.invalidateCache(), "calling the predicate twice must not consume the invalidation");
        //An explicit clear is not a publication: identity and epoch are untouched, and the binding is still current
        assertSame(binding, RecipeCacheContext.currentBinding());
        assertEquals(1, binding.epoch());
        assertSame(token, binding.identityToken());
        assertTrue(monitor.updateAndProcess(), "the explicit clear must force a re-resolution against the still-bound manager");
        assertSame(second, handler.cached.getRecipe());
        assertFalse(monitor.invalidateCache());

        //Rebinding even to the same manager and epoch is a fresh identity and therefore invalidates
        BasicCrushingRecipe third = crushing(Items.GOLD_INGOT, new ItemStack(Items.GOLD_BLOCK, 1));
        replace(third);
        handler.slot.stack = new ItemStack(Items.GOLD_INGOT, 4);
        RecipeCacheContext.Binding rebound = RecipeCacheContext.bind(manager, access, 1);
        assertNotSame(binding, rebound, "a rebind must produce a fresh binding");
        assertNotSame(token, rebound.identityToken(), "a rebind to the same manager and epoch must still be a fresh identity");
        assertEquals(1, rebound.epoch());
        assertTrue(monitor.invalidateCache(), "a same-manager same-epoch rebind must still invalidate");
        assertTrue(monitor.updateAndProcess());
        assertSame(third, handler.cached.getRecipe());
    }

    @Test
    void noContextReleasesAndRealLevelMismatchFailsBeforeProcessingStillValidCache() {
        BasicCrushingRecipe crushing = crushing(Items.IRON_INGOT, new ItemStack(Items.IRON_NUGGET, 9));
        replace(crushing);
        RecipeCacheContext.bind(manager, access, 1);
        InstantItemHandler handler = new InstantItemHandler();
        handler.level = RecipeLevelStub.of(manager);
        RecipeCacheLookupMonitor<ItemStackToItemStackRecipe> monitor = new RecipeCacheLookupMonitor<>(handler, 0);
        assertTrue(monitor.updateAndProcess());
        ProbeCachedRecipe cached = handler.probe;
        assertTrue(cached.isInputValid());

        //A real level whose manager is not the exact bound manager must fail even though the cached input is still valid
        handler.level = RecipeLevelStub.of(new RecipeManager(access));
        int processedBeforeMismatch = cached.processCalls;
        assertThrows(IllegalStateException.class, monitor::updateAndProcess,
              "a mismatched real level must fail before a still-input-valid cached recipe could bypass recipe lookup");
        assertEquals(processedBeforeMismatch, cached.processCalls, "the failure must happen before processing");

        //With no published context an old cached recipe must not be processed; it is detached and reported once
        handler.level = RecipeLevelStub.of(manager);
        assertTrue(monitor.updateAndProcess());
        assertTrue(cached.isInputValid());
        handler.recipeChanges = 0;
        int processedBeforeRelease = cached.processCalls;
        RecipeCacheContext.clear();
        assertNull(RecipeCacheContext.currentBinding());
        assertTrue(monitor.invalidateCache(), "no published context must always invalidate");
        assertFalse(monitor.updateAndProcess(), "no context must not process an old cached recipe");
        assertNull(monitor.getCachedRecipe(0), "the released cache must be detached");
        assertEquals(1, handler.recipeChanges, "the changed-to-null notification must be delivered exactly once");
        assertEquals(processedBeforeRelease, cached.processCalls, "the retained reference must not be processed after release");
        assertFalse(monitor.updateAndProcess());
        assertEquals(1, handler.recipeChanges, "the detached state must not re-report every tick");
    }

    @Test
    void constantChemicalMonitorRestoresTrueLongSavedUsageThroughBothFactories() {
        ChemicalStack output = new ChemicalStack(MekanismChemicals.STEAM, 5);
        BasicChemicalDissolutionRecipe recipe = new BasicChemicalDissolutionRecipe(
              IngredientCreatorAccess.item().from(Items.IRON_INGOT, 1),
              IngredientCreatorAccess.chemicalStack().from(IngredientCreatorAccess.chemical().of(MekanismChemicals.HYDROGEN.get().getAsHolder()), 1_000),
              output, true);
        replace(recipe);
        RecipeCacheContext.bind(manager, access, 1);

        DissolutionHandler handler = new DissolutionHandler();
        //Deliberately beyond the int range: a truncated restoration would lose the high bits
        long savedUsage = (1L << 55) + 7;
        handler.savedUsage = savedUsage;
        RecipeCacheLookupMonitor<ChemicalDissolutionRecipe> monitor = new RecipeCacheLookupMonitor<>(handler, 0);

        assertTrue(monitor.updateAndProcess());
        assertEquals(savedUsage, handler.cached.getRecipe() == recipe ? savedUsage : -1, "the real dissolution recipe must be cached");
        assertEquals(savedUsage, handler.observedUsedSoFar,
              "the monitor's loadSavedData branch must restore the full true-long constant chemical usage");
        assertSame(recipe, handler.cached.getRecipe());

        //Both preserved factories produce their nominal cached recipe, so the chemical branch is not a Crusher-only truncation
        assertNotNull(ItemStackConstantChemicalToObjectCachedRecipe.dissolution(recipe, () -> true, handler.itemHandler, handler.chemicalHandler,
              ChemicalUsageMultiplier.constantUse(() -> 1_000L, () -> 10), used -> {
              }, handler.outputHandler));
        BasicCompressingRecipe compressing = new BasicCompressingRecipe(
              IngredientCreatorAccess.item().from(Items.IRON_INGOT, 1),
              IngredientCreatorAccess.chemicalStack().from(IngredientCreatorAccess.chemical().of(MekanismChemicals.OXYGEN.get().getAsHolder()), 10),
              new ItemStack(Items.IRON_BLOCK, 1), false);
        assertNotNull(ItemStackConstantChemicalToObjectCachedRecipe.toItem(compressing, () -> true, handler.itemHandler, handler.chemicalHandler,
              ChemicalUsageMultiplier.constantUse(() -> 10L, () -> 10), used -> {
              }, new UnlimitedItemOutput()));
        assertFalse(compressing.perTickUsage());
        assertTrue(recipe.perTickUsage());
    }

    @Test
    void factorySelectionSortingCallbackAndEnergyReportingRespectTheSameOwnership() {
        BasicCrushingRecipe crushing = crushing(Items.IRON_INGOT, new ItemStack(Items.IRON_NUGGET, 9));
        replace(crushing);
        RecipeCacheContext.bind(manager, access, 1);
        InstantItemHandler handler = new InstantItemHandler();
        boolean[] sortingNeeded = {false};
        FactoryRecipeCacheLookupMonitor<ItemStackToItemStackRecipe> monitor = new FactoryRecipeCacheLookupMonitor<>(handler, 0, () -> sortingNeeded[0] = true);

        //onChange must still request sorting
        monitor.onChange();
        assertTrue(sortingNeeded[0], "the factory monitor must keep requesting sorting on change");

        //An explicit selection is the second state-write entry point: it becomes the cache and is retained on the next tick
        monitor.updateCachedRecipe(crushing);
        ProbeCachedRecipe selected = monitor.getCachedRecipe(0) instanceof ProbeCachedRecipe probe ? probe : null;
        assertEquals(1, handler.recipeCreates, "the explicit selection creates exactly one cached recipe");
        assertFalse(monitor.invalidateCache(), "an explicit selection must acknowledge the same binding as an automatic lookup");
        assertTrue(monitor.updateAndProcess());
        assertSame(selected, monitor.getCachedRecipe(0), "the next tick must preserve the explicitly selected recipe");
        assertEquals(1, handler.recipeCreates, "the next tick must not recreate or discard the selection");
        assertEquals(0, handler.savedTickReads, "the explicit selection must not spuriously reload saved state");

        //The energy helper reports the actual consumption and zero when there is no cached recipe
        EnergyContainer energy = new EnergyContainer(1_000);
        assertEquals(0L, monitor.updateAndProcess(energy), "a zero per-tick energy recipe consumes nothing");
        handler.slot.stack = ItemStack.EMPTY;
        MekanismRecipeType.clearCache();
        assertEquals(0L, monitor.updateAndProcess(energy), "no cached recipe must report zero energy usage");
    }

    @Test
    void everyNominalHelperShapeCompilesAndRoutesToTheCanonicalInputCache() {
        replace();
        RecipeCacheContext.bind(manager, access, 1);
        Level level = RecipeLevelStub.of(manager);

        //Every nominal helper shape across the five interfaces compiles against the real canonical providers
        SingleItemShape singleItem = new SingleItemShape(level);
        SingleFluidShape singleFluid = new SingleFluidShape(level);
        SingleChemicalShape singleChemical = new SingleChemicalShape(level);
        DoubleItemShape doubleItem = new DoubleItemShape(level);
        ItemChemicalShape itemChemical = new ItemChemicalShape(level);
        FluidChemicalShape fluidChemical = new FluidChemicalShape(level);
        EitherSideShape eitherSide = new EitherSideShape(level);
        TripleShape triple = new TripleShape(level);

        assertSame(MekanismRecipeType.CRUSHING.getInputCache(), singleItem.getRecipeType().getInputCache());
        assertSame(MekanismRecipeType.EVAPORATING.getInputCache(), singleFluid.getRecipeType().getInputCache());
        assertSame(MekanismRecipeType.ACTIVATING.getInputCache(), singleChemical.getRecipeType().getInputCache());
        assertSame(MekanismRecipeType.COMBINING.getInputCache(), doubleItem.getRecipeType().getInputCache());
        assertSame(MekanismRecipeType.COMPRESSING.getInputCache(), itemChemical.getRecipeType().getInputCache());
        assertSame(MekanismRecipeType.WASHING.getInputCache(), fluidChemical.getRecipeType().getInputCache());
        assertSame(MekanismRecipeType.CHEMICAL_INFUSING.getInputCache(), eitherSide.getRecipeType().getInputCache());
        assertSame(MekanismRecipeType.REACTION.getInputCache(), triple.getRecipeType().getInputCache());

        //The default helpers actually route through those caches rather than throwing or bypassing them
        assertFalse(singleItem.containsRecipe(new ItemStack(Items.IRON_INGOT)));
        assertFalse(singleFluid.containsRecipe(new FluidStack(Fluids.WATER, 1_000)));
        assertFalse(singleChemical.containsRecipe(new ChemicalStack(MekanismChemicals.HYDROGEN, 10)));
        assertFalse(doubleItem.containsRecipeAB(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)));
        assertFalse(itemChemical.containsRecipeA(new ItemStack(Items.IRON_INGOT)));
        assertFalse(fluidChemical.containsRecipeA(new FluidStack(Fluids.WATER, 1_000)));
        assertFalse(eitherSide.containsRecipe(new ChemicalStack(MekanismChemicals.HYDROGEN, 10)));
        assertFalse(triple.containsRecipeABC(new ItemStack(Items.IRON_INGOT), new FluidStack(Fluids.WATER, 1_000),
              new ChemicalStack(MekanismChemicals.STEAM, 5)));
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

    private static void replace(mekanism.api.recipes.MekanismRecipe<?>... recipes) {
        List<RecipeHolder<?>> holders = new ArrayList<>();
        for (int i = 0; i < recipes.length; i++) {
            holders.add(new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("mekanism_test", "recipe_" + i), recipes[i]));
        }
        manager.replaceRecipes(holders);
    }

    private static BasicCrushingRecipe crushing(net.minecraft.world.item.Item input, ItemStack output) {
        return new BasicCrushingRecipe(IngredientCreatorAccess.item().from(input, 1), output);
    }

    /** Real single-input cached recipe probe exposing processing and unpause counts. */
    private static final class ProbeCachedRecipe extends OneInputCachedRecipe<ItemStack, ItemStack, ItemStackToItemStackRecipe> {

        private int processCalls;
        private int unpauseCalls;
        private int loadedOperatingTicks = Integer.MIN_VALUE;

        private ProbeCachedRecipe(ItemStackToItemStackRecipe recipe, IInputHandler<ItemStack> inputHandler, IOutputHandler<ItemStack> outputHandler) {
            super(recipe, () -> true, inputHandler, outputHandler, recipe::getInput, recipe::getOutput,
                  mekanism.api.functions.ConstantPredicates.ITEM_EMPTY, mekanism.api.functions.ConstantPredicates.ITEM_EMPTY);
        }

        @Override
        public void loadSavedOperatingTicks(int operatingTicks) {
            loadedOperatingTicks = operatingTicks;
            super.loadSavedOperatingTicks(operatingTicks);
        }

        @Override
        public void process() {
            processCalls++;
            super.process();
        }

        @Override
        public void unpauseErrors() {
            unpauseCalls++;
            super.unpauseErrors();
        }
    }

    /** Base handler providing the non-lookup members of {@link IRecipeLookupHandler}. */
    private abstract static class BaseHandler<RECIPE extends mekanism.api.recipes.MekanismRecipe<?>, CACHE extends mekanism.common.recipe.lookup.cache.IInputRecipeCache>
          implements IRecipeLookupHandler<RECIPE> {
        private final IMekanismRecipeTypeProvider<?, RECIPE, CACHE> provider;
        Level level;
        int savedTicks;
        int lastOperatingTicks;
        int recipeLookups;
        int recipeChanges;
        int recipeCreates;
        int savedTickReads;
        CachedRecipe<RECIPE> cached;
        Runnable onContentsChangedHook;

        @Override
        public void onContentsChanged() {
            if (onContentsChangedHook != null) {
                onContentsChangedHook.run();
            }
        }

        private BaseHandler(IMekanismRecipeTypeProvider<?, RECIPE, CACHE> provider) {
            this.provider = provider;
        }

        @Override
        public Level getLevel() {
            return level;
        }

        @Override
        public IMekanismRecipeTypeProvider<?, RECIPE, CACHE> getRecipeType() {
            return provider;
        }


        @Override
        public int getSavedOperatingTicks(int cacheIndex) {
            savedTickReads++;
            return savedTicks;
        }

        @Override
        public void onCachedRecipeChanged(CachedRecipe<RECIPE> cachedRecipe, int cacheIndex) {
            recipeChanges++;
            if (cachedRecipe != null) {
                cachedRecipe.setOperatingTicksChanged(ticks -> lastOperatingTicks = ticks);
            }
            //Mirror the default behaviour without the clearRecipeErrors notification being lost when a real cache is reported
            if (cachedRecipe == null) {
                clearRecipeErrors(cacheIndex);
            }
        }
    }

    /** Real single-item handler over the canonical crushing owner. */
    private static final class InstantItemHandler extends BaseHandler<ItemStackToItemStackRecipe, SingleItem<ItemStackToItemStackRecipe>>
          implements ISingleRecipeLookupHandler.ItemRecipeLookupHandler<ItemStackToItemStackRecipe> {
        final ItemSlot slot = new ItemSlot(new ItemStack(Items.IRON_INGOT, 8));
        private ProbeCachedRecipe probe;

        private InstantItemHandler() {
            super(MekanismRecipeType.CRUSHING);
        }
        private final IInputHandler<ItemStack> inputHandler = InputHelper.getInputHandler(slot, RecipeError.NOT_ENOUGH_INPUT);
        private final IOutputHandler<ItemStack> outputHandler = new UnlimitedItemOutput();

        @Override
        public ItemStackToItemStackRecipe getRecipe(int cacheIndex) {
            recipeLookups++;
            ItemStack input = slot.getStack();
            return input.isEmpty() ? null : findFirstRecipe(inputHandler);
        }

        @Override
        public CachedRecipe<ItemStackToItemStackRecipe> createNewCachedRecipe(ItemStackToItemStackRecipe recipe, int cacheIndex) {
            recipeCreates++;
            cached = probe = new ProbeCachedRecipe(recipe, inputHandler, outputHandler);
            //A multi-tick recipe so the persisted operating tick count is meaningful rather than always below the required ticks
            cached.setRequiredTicks(() -> 10);
            return cached;
        }
    }

    /** Real constant-chemical handler over the canonical dissolution owner. */
    private static final class DissolutionHandler extends BaseHandler<ChemicalDissolutionRecipe, ItemChemical<ChemicalDissolutionRecipe>>
          implements IDoubleRecipeLookupHandler.ItemChemicalRecipeLookupHandler<ChemicalDissolutionRecipe>,
          IRecipeLookupHandler.ConstantUsageRecipeLookupHandler {
        private final BasicChemicalTank tank = (BasicChemicalTank) BasicChemicalTank.createAllValid(Long.MAX_VALUE, null);
        final IInputHandler<ItemStack> itemHandler = InputHelper.getInputHandler(new ItemSlot(new ItemStack(Items.IRON_INGOT, 8)), RecipeError.NOT_ENOUGH_INPUT);
        final ILongInputHandler<ChemicalStack> chemicalHandler;
        final IOutputHandler<ChemicalStack> outputHandler = new UnlimitedChemicalOutput();
        private long savedUsage;
        private long observedUsedSoFar = Long.MIN_VALUE;

        private DissolutionHandler() {
            super(MekanismRecipeType.DISSOLUTION);
            tank.setStack(new ChemicalStack(MekanismChemicals.HYDROGEN, Long.MAX_VALUE));
            chemicalHandler = InputHelper.getConstantInputHandler(tank);
        }

        @Override
        public ChemicalDissolutionRecipe getRecipe(int cacheIndex) {
            recipeLookups++;
            return findFirstRecipe(itemHandler, chemicalHandler);
        }

        @Override
        public CachedRecipe<ChemicalDissolutionRecipe> createNewCachedRecipe(ChemicalDissolutionRecipe recipe, int cacheIndex) {
            recipeCreates++;
            ChemicalUsageMultiplier usage = (usedSoFar, operatingTicks) -> {
                observedUsedSoFar = usedSoFar;
                return ChemicalUsageMultiplier.constantUse(() -> 1_000L, () -> 10).getToUse(usedSoFar, operatingTicks);
            };
            return cached = ItemStackConstantChemicalToObjectCachedRecipe.dissolution(recipe, () -> true, itemHandler, chemicalHandler, usage,
                  used -> {
                  }, outputHandler);
        }

        @Override
        public long getSavedUsedSoFar(int cacheIndex) {
            return savedUsage;
        }
    }

    static final class ItemSlot implements IInventorySlot {
        ItemStack stack;
        private final int limit = 64;

        private ItemSlot(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public ItemStack getStack() {
            return stack;
        }

        @Override
        public void setStack(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int getLimit(ItemStack stack) {
            return limit;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return true;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            return new CompoundTag();
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        }

        @Override
        public void onContentsChanged() {
        }
    }

    private static final class UnlimitedItemOutput implements IOutputHandler<ItemStack> {
        @Override
        public void handleOutput(ItemStack toOutput, int operations) {
        }

        @Override
        public void calculateOperationsCanSupport(mekanism.api.recipes.cache.CachedRecipe.OperationTracker tracker, ItemStack toOutput) {
        }
    }

    private static final class UnlimitedChemicalOutput implements IOutputHandler<ChemicalStack> {
        @Override
        public void handleOutput(ChemicalStack toOutput, int operations) {
        }

        @Override
        public void calculateOperationsCanSupport(mekanism.api.recipes.cache.CachedRecipe.OperationTracker tracker, ChemicalStack toOutput) {
        }
    }

    private static final class EnergyContainer implements IEnergyContainer {
        private long energy;

        private EnergyContainer(long energy) {
            this.energy = energy;
        }

        @Override
        public long getEnergy() {
            return energy;
        }

        @Override
        public void setEnergy(long energy) {
            this.energy = energy;
        }

        @Override
        public long getMaxEnergy() {
            return 1_000;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            return new CompoundTag();
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        }

        @Override
        public void onContentsChanged() {
        }
    }

    /** Compile-only nominal helper shapes: each one binds the real canonical provider for its recipe family. */
    private abstract static class ShapeBase<RECIPE extends mekanism.api.recipes.MekanismRecipe<?>, CACHE extends mekanism.common.recipe.lookup.cache.IInputRecipeCache>
          extends BaseHandler<RECIPE, CACHE> {

        private ShapeBase(Level level, IMekanismRecipeTypeProvider<?, RECIPE, CACHE> provider) {
            super(provider);
            this.level = level;
        }

        @Override
        public RECIPE getRecipe(int cacheIndex) {
            return null;
        }

        @Override
        public CachedRecipe<RECIPE> createNewCachedRecipe(RECIPE recipe, int cacheIndex) {
            return null;
        }
    }

    private static final class SingleItemShape extends ShapeBase<ItemStackToItemStackRecipe, SingleItem<ItemStackToItemStackRecipe>>
          implements ISingleRecipeLookupHandler.ItemRecipeLookupHandler<ItemStackToItemStackRecipe> {
        private SingleItemShape(Level level) {
            super(level, MekanismRecipeType.CRUSHING);
        }

        @Override
        public IMekanismRecipeTypeProvider<?, ItemStackToItemStackRecipe, SingleItem<ItemStackToItemStackRecipe>> getRecipeType() {
            return MekanismRecipeType.CRUSHING;
        }
    }

    private static final class SingleFluidShape extends ShapeBase<FluidToFluidRecipe, SingleFluid<FluidToFluidRecipe>>
          implements ISingleRecipeLookupHandler.FluidRecipeLookupHandler<FluidToFluidRecipe> {
        private SingleFluidShape(Level level) {
            super(level, MekanismRecipeType.EVAPORATING);
        }

        @Override
        public IMekanismRecipeTypeProvider<?, FluidToFluidRecipe, SingleFluid<FluidToFluidRecipe>> getRecipeType() {
            return MekanismRecipeType.EVAPORATING;
        }
    }

    private static final class SingleChemicalShape extends ShapeBase<ChemicalToChemicalRecipe, SingleChemical<ChemicalToChemicalRecipe>>
          implements ISingleRecipeLookupHandler.ChemicalRecipeLookupHandler<ChemicalToChemicalRecipe> {
        private SingleChemicalShape(Level level) {
            super(level, MekanismRecipeType.ACTIVATING);
        }

        @Override
        public IMekanismRecipeTypeProvider<?, ChemicalToChemicalRecipe, SingleChemical<ChemicalToChemicalRecipe>> getRecipeType() {
            return MekanismRecipeType.ACTIVATING;
        }
    }

    private static final class DoubleItemShape extends ShapeBase<CombinerRecipe, DoubleItem<CombinerRecipe>> implements IDoubleRecipeLookupHandler.DoubleItemRecipeLookupHandler<CombinerRecipe> {
        private DoubleItemShape(Level level) {
            super(level, MekanismRecipeType.COMBINING);
        }

        @Override
        public IMekanismRecipeTypeProvider<?, CombinerRecipe, DoubleItem<CombinerRecipe>> getRecipeType() {
            return MekanismRecipeType.COMBINING;
        }
    }

    private static final class ItemChemicalShape extends ShapeBase<ItemStackChemicalToItemStackRecipe, ItemChemical<ItemStackChemicalToItemStackRecipe>>
          implements IDoubleRecipeLookupHandler.ItemChemicalRecipeLookupHandler<ItemStackChemicalToItemStackRecipe> {
        private ItemChemicalShape(Level level) {
            super(level, MekanismRecipeType.COMPRESSING);
        }

        @Override
        public IMekanismRecipeTypeProvider<?, ItemStackChemicalToItemStackRecipe, ItemChemical<ItemStackChemicalToItemStackRecipe>> getRecipeType() {
            return MekanismRecipeType.COMPRESSING;
        }
    }

    private static final class FluidChemicalShape extends ShapeBase<FluidChemicalToChemicalRecipe, FluidChemical<FluidChemicalToChemicalRecipe>>
          implements IDoubleRecipeLookupHandler.FluidChemicalRecipeLookupHandler<FluidChemicalToChemicalRecipe> {
        private FluidChemicalShape(Level level) {
            super(level, MekanismRecipeType.WASHING);
        }

        @Override
        public IMekanismRecipeTypeProvider<?, FluidChemicalToChemicalRecipe, FluidChemical<FluidChemicalToChemicalRecipe>> getRecipeType() {
            return MekanismRecipeType.WASHING;
        }
    }

    private static final class EitherSideShape extends ShapeBase<ChemicalChemicalToChemicalRecipe, EitherSideChemical<ChemicalChemicalToChemicalRecipe>>
          implements IEitherSideRecipeLookupHandler.EitherSideChemicalRecipeLookupHandler<ChemicalChemicalToChemicalRecipe> {
        private EitherSideShape(Level level) {
            super(level, MekanismRecipeType.CHEMICAL_INFUSING);
        }

        @Override
        public IMekanismRecipeTypeProvider<?, ChemicalChemicalToChemicalRecipe, EitherSideChemical<ChemicalChemicalToChemicalRecipe>> getRecipeType() {
            return MekanismRecipeType.CHEMICAL_INFUSING;
        }
    }

    private static final class TripleShape extends ShapeBase<PressurizedReactionRecipe, ItemFluidChemical<PressurizedReactionRecipe>>
          implements ITripleRecipeLookupHandler.ItemFluidChemicalRecipeLookupHandler<PressurizedReactionRecipe> {
        private TripleShape(Level level) {
            super(level, MekanismRecipeType.REACTION);
        }

        @Override
        public IMekanismRecipeTypeProvider<?, PressurizedReactionRecipe, ItemFluidChemical<PressurizedReactionRecipe>> getRecipeType() {
            return MekanismRecipeType.REACTION;
        }
    }

    /** Kept private to the fixture so production keeps owning the real registration entry point. */
    private static final class MekanismChemicalIngredientTypesRegistration {
        private static void ensure() {
            mekanism.common.registries.MekanismChemicalIngredientTypes.ensureRegistered();
        }
    }
}
