package mekanism.api.recipes.inputs;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.FluidStack;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.cache.CachedRecipeHelper;
import mekanism.api.recipes.cache.ICachedRecipeHolder;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.common.registries.MekanismChemicals;
import mekanism.test.ChemicalTestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputHelperExecutionTest {

    @BeforeAll
    static void bootstrap() {
        ChemicalTestBootstrap.initialize();
    }

    @Test
    void itemInputHandlerConsumesExactAmountsAndCalculatesOperations() throws Exception {
        ItemSlot slot = new ItemSlot(new ItemStack(Items.IRON_INGOT, 7), 64);
        IInputHandler<ItemStack> handler = InputHelper.getInputHandler(slot, RecipeError.NOT_ENOUGH_INPUT);
        ItemStackIngredient ingredient = IngredientCreatorAccess.item().from(Items.IRON_INGOT, 2);
        ItemStack recipeInput = handler.getRecipeInput(ingredient);
        assertEquals(2, recipeInput.getCount());
        handler.use(recipeInput, 3);
        assertEquals(1, slot.stack.getCount(), "three two-item operations consume six of the seven items");
        Probe probe = Probe.create();
        handler.calculateOperationsCanSupport(probe.tracker(), recipeInput, 1);
        assertEquals(-1, probe.max(), "an insufficient ordinary input resets progress through the negative sentinel");
        slot.stack = new ItemStack(Items.IRON_INGOT, 5);
        Probe second = Probe.create();
        handler.calculateOperationsCanSupport(second.tracker(), recipeInput, 1);
        assertEquals(2, second.max(), "five items sustain two operations");
        slot.stack = ItemStack.EMPTY;
        Probe mismatched = Probe.create();
        handler.getRecipeInput(ingredient);
        assertTrue(handler.getRecipeInput(ingredient).isEmpty(), "an emptied slot produces no recipe input");
    }

    @Test
    void fluidInputHandlerConsumesMbAmounts() throws Exception {
        FluidTank tank = new FluidTank(new FluidStack(Fluids.WATER, 1_000), 10_000);
        IInputHandler<FluidStack> handler = InputHelper.getInputHandler(tank, RecipeError.NOT_ENOUGH_INPUT);
        FluidStack recipeInput = handler.getRecipeInput(IngredientCreatorAccess.fluid().fromHolder(Fluids.WATER.builtInRegistryHolder(), 250));
        assertEquals(250, recipeInput.getAmount());
        handler.use(recipeInput, 4);
        assertEquals(0, tank.stack.getAmount(), "1000 mB minus four 250 mB operations is empty");
        tank.stack = new FluidStack(Fluids.WATER, 600);
        Probe probe = Probe.create();
        handler.calculateOperationsCanSupport(probe.tracker(), recipeInput, 1);
        assertEquals(2, probe.max(), "600 mB sustains two 250 mB operations");
    }

    @Test
    void chemicalInputHandlerConsumesTrueLongAmounts() throws Exception {
        BasicChemicalTank tank = (BasicChemicalTank) BasicChemicalTank.createAllValid(Long.MAX_VALUE, null);
        long amount = (1L << 55) + 11;
        tank.setStack(new ChemicalStack(MekanismChemicals.HYDROGEN, amount));
        ILongInputHandler<ChemicalStack> handler = InputHelper.getInputHandler(tank, RecipeError.NOT_ENOUGH_INPUT);
        ChemicalStack recipeInput = handler.getRecipeInput(IngredientCreatorAccess.chemicalStack()
              .from(IngredientCreatorAccess.chemical().of(MekanismChemicals.HYDROGEN.get().getAsHolder()), 1_000));
        assertEquals(1_000, recipeInput.getAmount());
        handler.use(recipeInput, 1_000);
        assertEquals(amount - 1_000_000L, tank.getStored());
        Probe probe = Probe.create();
        handler.calculateOperationsCanSupport(probe.tracker(), recipeInput, 1);
        assertEquals(Integer.MAX_VALUE, probe.max(), "true-long storage saturates the int operation count");
    }

    @Test
    void constantChemicalHandlerPausesInsteadOfResettingProgress() throws Exception {
        BasicChemicalTank tank = (BasicChemicalTank) BasicChemicalTank.createAllValid(Long.MAX_VALUE, null);
        tank.setStack(new ChemicalStack(MekanismChemicals.STEAM, 10));
        ILongInputHandler<ChemicalStack> constant = InputHelper.getConstantInputHandler(tank);
        ChemicalStack recipeInput = constant.getRecipeInput(IngredientCreatorAccess.chemicalStack()
              .from(IngredientCreatorAccess.chemical().of(MekanismChemicals.STEAM.get().getAsHolder()), 100));
        assertTrue(recipeInput.isEmpty(), "ten stored units cannot match a one-hundred-unit ingredient");
        Probe probe = Probe.create();
        constant.calculateOperationsCanSupport(probe.tracker(), recipeInput, 1);
        assertEquals(0, probe.max(), "constant usage pauses at zero operations");
        //The ordinary handler resets progress instead of pausing for the same shortage
        tank.setStack(new ChemicalStack(MekanismChemicals.STEAM, 10));
        ILongInputHandler<ChemicalStack> ordinary = InputHelper.getInputHandler(tank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT);
        ChemicalStack ordinaryInput = ordinary.getRecipeInput(IngredientCreatorAccess.chemicalStack()
              .from(IngredientCreatorAccess.chemical().of(MekanismChemicals.STEAM.get().getAsHolder()), 100));
        assertTrue(ordinaryInput.isEmpty());
        Probe reset = Probe.create();
        ordinary.calculateOperationsCanSupport(reset.tracker(), ordinaryInput, 1);
        assertEquals(-1, reset.max(), "the ordinary handler resets progress through the negative sentinel");
    }

    @Test
    void cachedRecipeSchedulesOperationsErrorsEnergyAndTicks() throws Exception {
        EnergyContainer energy = new EnergyContainer(1_000);
        ItemSlot slot = new ItemSlot(new ItemStack(Items.IRON_INGOT, 10), 64);
        ItemOutput output = new ItemOutput();
        MachineRecipe recipe = new MachineRecipe(IngredientCreatorAccess.item().from(Items.IRON_INGOT, 2));
        ProcessingCachedRecipe cached = new ProcessingCachedRecipe(recipe, InputHelper.getInputHandler(slot, RecipeError.NOT_ENOUGH_INPUT), output);
        cached.setEnergyRequirements(() -> 100L, energy);
        cached.setRequiredTicks(() -> 3);
        cached.process();
        assertEquals(0, output.added, "no output before the ticks elapse");
        assertEquals(900, energy.getEnergy(), "energy is charged per tick");
        assertEquals(8, slot.stack.getCount(), "each tick's single operation consumes its input");
        cached.process();
        assertEquals(800, energy.getEnergy());
        cached.process();
        assertEquals(700, energy.getEnergy());
        assertEquals(2, output.added, "the third tick finishes the recipe and emits the two-nugget output");
        assertEquals(4, slot.stack.getCount());
        //Insufficient energy pauses processing with the energy error rather than resetting
        energy.setEnergy(50);
        cached.process();
        assertEquals(50, energy.getEnergy());
        assertTrue(cached.errorsSeen.contains(RecipeError.NOT_ENOUGH_ENERGY));
        //An emptied input mismatches the recipe: progress resets and errors clear
        slot.stack = ItemStack.EMPTY;
        cached.unpauseErrors();
        cached.process();
        assertFalse(cached.errorsSeen.contains(RecipeError.NOT_ENOUGH_ENERGY), "a mismatched recipe clears errors");
    }

    @Test
    void helperShortCircuitOutputOrderingAndHolderInvalidation() {
        OrderRecorder recorder = new OrderRecorder();
        ItemSlot slot = new ItemSlot(new ItemStack(Items.GOLD_INGOT, 9), 64);
        MachineRecipe recipe = new MachineRecipe(IngredientCreatorAccess.item().from(Items.GOLD_INGOT, 3));
        IInputHandler<ItemStack> recordingInput = new RecordingInputHandler(InputHelper.getInputHandler(slot, RecipeError.NOT_ENOUGH_INPUT), recorder);
        IOutputHandler<ItemStack> recordingOutput = new RecordingOutputHandler(new ItemOutput(), recorder);
        ProcessingCachedRecipe cached = new ProcessingCachedRecipe(recipe, recordingInput, recordingOutput);
        cached.process();
        assertEquals(List.of("input", "operations", "output"), recorder.events,
              "the helper resolves the input, calculates the supported operations, then checks the output room, in order");

        HolderFlow holder = new HolderFlow(recipe, slot);
        assertNull(holder.getCachedRecipe(0));
        CachedRecipe<MachineRecipe> updated = holder.getUpdatedCache(0);
        assertNotNull(updated, "a matching recipe creates a fresh cache");
        assertSame(updated, holder.getUpdatedCache(0), "a still-valid cache is retained");
        assertEquals(0, holder.noRecipeFlags);
        slot.stack = ItemStack.EMPTY;
        holder.invalidate = true;
        assertNull(holder.getUpdatedCache(0), "no recipe matches the emptied input");
        assertTrue(holder.noRecipeFlags > 0, "the missing recipe is memoized to short circuit following lookups");
        assertNull(holder.getUpdatedCache(0), "the memoized lookup does not re-consult the recipe");
    }

    /** Real tracker handle built through the same private constructor the cached recipe uses. */
    private static final class Probe {
        private final OperationTracker tracker;
        private final Field max;

        private Probe(OperationTracker tracker, Field max) {
            this.tracker = tracker;
            this.max = max;
        }

        static Probe create() throws Exception {
            Constructor<OperationTracker> constructor = OperationTracker.class.getDeclaredConstructor(Set.class, boolean.class, int.class);
            constructor.setAccessible(true);
            Field max = OperationTracker.class.getDeclaredField("currentMax");
            max.setAccessible(true);
            return new Probe(constructor.newInstance(Set.of(), true, Integer.MAX_VALUE), max);
        }

        OperationTracker tracker() {
            return tracker;
        }

        int max() throws Exception {
            return max.getInt(tracker);
        }
    }

    /** Minimal recipe used by the generic execution fixtures. */
    private static final class MachineRecipe extends MekanismRecipe<RecipeInput> implements Predicate<ItemStack> {
        private static final RecipeType<MachineRecipe> TYPE = new RecipeType<>() {
        };
        private final ItemStackIngredient input;

        private MachineRecipe(ItemStackIngredient input) {
            this.input = input;
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
            return SERIALIZER;
        }

        private static final net.minecraft.world.item.crafting.RecipeSerializer<MachineRecipe> SERIALIZER = new net.minecraft.world.item.crafting.RecipeSerializer<>() {
            @Override
            public com.mojang.serialization.MapCodec<MachineRecipe> codec() {
                throw new UnsupportedOperationException("Fixture serializer has no codec");
            }

            @Override
            public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, MachineRecipe> streamCodec() {
                throw new UnsupportedOperationException("Fixture serializer has no stream codec");
            }
        };
    }

    /** Single-input cached recipe built from the generic helper, matching the official one-input execution shape. */
    private static class ProcessingCachedRecipe extends CachedRecipe<MachineRecipe> {
        private final IInputHandler<ItemStack> inputHandler;
        private final IOutputHandler<ItemStack> outputHandler;
        private ItemStack cachedInput = ItemStack.EMPTY;
        private final List<RecipeError> errorsSeen = new ArrayList<>();

        private ProcessingCachedRecipe(MachineRecipe recipe, IInputHandler<ItemStack> inputHandler, IOutputHandler<ItemStack> outputHandler) {
            super(recipe, () -> true);
            this.inputHandler = inputHandler;
            this.outputHandler = outputHandler;
            setErrorsChanged(errors -> {
                errorsSeen.clear();
                errorsSeen.addAll(errors);
            });
        }

        @Override
        protected void calculateOperationsThisTick(OperationTracker tracker) {
            super.calculateOperationsThisTick(tracker);
            CachedRecipeHelper.oneInputCalculateOperationsThisTick(tracker, inputHandler, () -> recipe.input, input -> cachedInput = input, outputHandler,
                  input -> new ItemStack(Items.IRON_NUGGET, 2), output -> { }, ItemStack::isEmpty);
        }

        @Override
        protected void useResources(int operations) {
            inputHandler.use(cachedInput, operations);
        }

        @Override
        public boolean isInputValid() {
            ItemStack input = inputHandler.getInput();
            return !input.isEmpty() && recipe.input.test(input);
        }

        @Override
        protected void finishProcessing(int operations) {
            inputHandler.use(cachedInput, operations);
            outputHandler.handleOutput(new ItemStack(Items.IRON_NUGGET, 2), operations);
        }
    }

    private static final class RecordingInputHandler implements IInputHandler<ItemStack> {
        private final IInputHandler<ItemStack> inner;
        private final OrderRecorder recorder;

        private RecordingInputHandler(IInputHandler<ItemStack> inner, OrderRecorder recorder) {
            this.inner = inner;
            this.recorder = recorder;
        }

        @Override
        public ItemStack getInput() {
            return inner.getInput();
        }

        @Override
        public ItemStack getRecipeInput(mekanism.api.recipes.ingredients.InputIngredient<ItemStack> recipeIngredient) {
            recorder.record("input");
            return inner.getRecipeInput(recipeIngredient);
        }

        @Override
        public void use(ItemStack recipeInput, int operations) {
            inner.use(recipeInput, operations);
        }

        @Override
        public void calculateOperationsCanSupport(OperationTracker tracker, ItemStack recipeInput, int usageMultiplier) {
            recorder.record("operations");
            inner.calculateOperationsCanSupport(tracker, recipeInput, usageMultiplier);
        }
    }

    private static final class RecordingOutputHandler implements IOutputHandler<ItemStack> {
        private final IOutputHandler<ItemStack> inner;
        private final OrderRecorder recorder;

        private RecordingOutputHandler(IOutputHandler<ItemStack> inner, OrderRecorder recorder) {
            this.inner = inner;
            this.recorder = recorder;
        }

        @Override
        public void handleOutput(ItemStack toOutput, int operations) {
            inner.handleOutput(toOutput, operations);
        }

        @Override
        public void calculateOperationsCanSupport(OperationTracker tracker, ItemStack toOutput) {
            recorder.record("output");
            inner.calculateOperationsCanSupport(tracker, toOutput);
        }
    }

    private static final class OrderRecorder {
        private final List<String> events = new ArrayList<>();

        void record(String event) {
            events.add(event);
        }
    }

    private static final class ItemOutput implements IOutputHandler<ItemStack> {
        private int added;

        @Override
        public void handleOutput(ItemStack toOutput, int operations) {
            added += toOutput.getCount() * operations;
        }

        @Override
        public void calculateOperationsCanSupport(OperationTracker tracker, ItemStack toOutput) {
            //Unlimited output room for the fixture
        }
    }

    private static final class EnergyContainer implements IEnergyContainer {
        private long energy;
        private final long maxEnergy;

        private EnergyContainer(long maxEnergy) {
            this.maxEnergy = maxEnergy;
            this.energy = maxEnergy;
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
            return maxEnergy;
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

    private static final class HolderFlow implements ICachedRecipeHolder<MachineRecipe> {
        private final MachineRecipe recipe;
        private final ItemSlot slot;
        private CachedRecipe<MachineRecipe> cached;
        private boolean invalidate;
        private int noRecipeFlags;

        private HolderFlow(MachineRecipe recipe, ItemSlot slot) {
            this.recipe = recipe;
            this.slot = slot;
        }

        @Override
        public CachedRecipe<MachineRecipe> getCachedRecipe(int cacheIndex) {
            return cached;
        }

        @Override
        public MachineRecipe getRecipe(int cacheIndex) {
            ItemStack input = slot.getStack();
            return input.isEmpty() || !recipe.test(input) ? null : recipe;
        }

        @Override
        public CachedRecipe<MachineRecipe> createNewCachedRecipe(MachineRecipe recipe, int cacheIndex) {
            cached = new ProcessingCachedRecipe(recipe, InputHelper.getInputHandler(slot, RecipeError.NOT_ENOUGH_INPUT), new ItemOutput());
            return cached;
        }

        @Override
        public boolean invalidateCache() {
            return invalidate;
        }

        @Override
        public void setHasNoRecipe(int cacheIndex) {
            noRecipeFlags++;
        }

        @Override
        public boolean hasNoRecipe(int cacheIndex) {
            return noRecipeFlags > 0;
        }
    }

    private static final class ItemSlot implements IInventorySlot {
        private ItemStack stack;
        private final int limit;

        private ItemSlot(ItemStack stack, int limit) {
            this.stack = stack;
            this.limit = limit;
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

    private static final class FluidTank implements IExtendedFluidTank {
        private FluidStack stack;
        private final int capacity;

        private FluidTank(FluidStack stack, int capacity) {
            this.stack = stack;
            this.capacity = capacity;
        }

        @Override
        public FluidStack getFluid() {
            return stack;
        }

        @Override
        public void setStack(FluidStack stack) {
            this.stack = stack;
        }

        @Override
        public void setStackUnchecked(FluidStack stack) {
            setStack(stack);
        }

        @Override
        public int getCapacity() {
            return capacity;
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            return true;
        }

        @Override
        public void onContentsChanged() {
        }
    }
}
