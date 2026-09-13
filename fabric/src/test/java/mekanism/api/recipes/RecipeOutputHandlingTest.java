package mekanism.api.recipes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.fluid.FluidStack;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.ElectrolysisRecipe.ElectrolysisRecipeOutput;
import mekanism.api.recipes.ItemStackToFluidOptionalItemRecipe.FluidOptionalItemOutput;
import mekanism.api.recipes.PressurizedReactionRecipe.PressurizedReactionRecipeOutput;
import mekanism.api.recipes.SawmillRecipe.ChanceOutput;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.cache.OneInputCachedRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.api.recipes.outputs.OutputHelper;
import mekanism.common.registries.MekanismChemicals;
import mekanism.test.ChemicalTestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ordinary (non-Knot) execution contracts for the Task 042A output handling and one-input cached recipe vertical. Nothing here mutates a registry or touches the
 * network; recipe types are exercised only through their identity stable keyed holders.
 */
@SuppressWarnings("removal")
class RecipeOutputHandlingTest {

    @BeforeAll
    static void bootstrap() {
        ChemicalTestBootstrap.initialize();
    }

    @Test
    void chemicalTankOutputHandlerGrowsTrueLongAndChoosesTheRightError() throws Exception {
        IChemicalTank tank = BasicChemicalTank.createAllValid(Long.MAX_VALUE, null);
        ChemicalStack hydrogen = hydrogen(2_000_000_000L);
        IOutputHandler<ChemicalStack> handler = OutputHelper.getOutputHandler(tank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
        handler.handleOutput(hydrogen, 3);
        assertEquals(6_000_000_000L, tank.getStored(), "amount times operations must not be truncated to int");

        IChemicalTank untouched = BasicChemicalTank.createAllValid(1_000, null);
        OutputHelper.getOutputHandler(untouched, RecipeError.NOT_ENOUGH_OUTPUT_SPACE).handleOutput(hydrogen, 0);
        assertEquals(0, untouched.getStored(), "zero operations must not insert anything");

        IChemicalTank limited = BasicChemicalTank.createAllValid(10, null);
        Probe limitedProbe = Probe.create(true, Integer.MAX_VALUE);
        OutputHelper.getOutputHandler(limited, RecipeError.NOT_ENOUGH_OUTPUT_SPACE)
              .calculateOperationsCanSupport(limitedProbe.tracker(), hydrogen(4));
        assertEquals(2, limitedProbe.max(), "ten units of room hold two four-unit outputs");

        IChemicalTank rejecting = BasicChemicalTank.createModern(1_000, ConstantPredicates.alwaysTrue(), ConstantPredicates.alwaysFalse(), null);
        Probe rejectingProbe = Probe.create(true, Integer.MAX_VALUE);
        OutputHelper.getOutputHandler(rejecting, RecipeError.NOT_ENOUGH_OUTPUT_SPACE)
              .calculateOperationsCanSupport(rejectingProbe.tracker(), hydrogen(5));
        assertEquals(0, rejectingProbe.max());
        assertTrue(rejectingProbe.errors().contains(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT),
              "a rejecting tank that still has space reports that the input produces no output");

        IChemicalTank full = BasicChemicalTank.createAllValid(1_000, null);
        assertTrue(full.insert(hydrogen(1_000), Action.EXECUTE, AutomationType.INTERNAL).isEmpty(), "the tank starts full");
        Probe fullProbe = Probe.create(true, Integer.MAX_VALUE);
        OutputHelper.getOutputHandler(full, RecipeError.NOT_ENOUGH_OUTPUT_SPACE)
              .calculateOperationsCanSupport(fullProbe.tracker(), hydrogen(5));
        assertEquals(0, fullProbe.max());
        assertTrue(fullProbe.errors().contains(RecipeError.NOT_ENOUGH_OUTPUT_SPACE), "a full tank reports not enough space");
        assertFalse(fullProbe.errors().contains(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
    }

    @Test
    void fluidTankOutputHandlerCapsSimulationAndTruncatesDivision() throws Exception {
        FluidTank tank = new FluidTank(1_000);
        IOutputHandler<FluidStack> handler = OutputHelper.getOutputHandler(tank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
        handler.handleOutput(water(250), 4);
        assertEquals(1_000, tank.stack.getAmount(), "the execute path inserts amount times operations");

        FluidTank paused = new FluidTank(1_000);
        OutputHelper.getOutputHandler(paused, RecipeError.NOT_ENOUGH_OUTPUT_SPACE).handleOutput(water(250), 0);
        assertEquals(0, paused.stack.getAmount(), "zero operations must not insert anything");

        FluidTank truncated = new FluidTank(1_000);
        Probe truncatedProbe = Probe.create(true, Integer.MAX_VALUE);
        OutputHelper.getOutputHandler(truncated, RecipeError.NOT_ENOUGH_OUTPUT_SPACE)
              .calculateOperationsCanSupport(truncatedProbe.tracker(), water(300));
        assertEquals(3, truncatedProbe.max(), "1,000 units of room truncate to three 300-unit outputs");
        assertEquals(0, truncated.stack.getAmount(), "the calculate path must only simulate");

        FluidTank capped = new FluidTank(Integer.MAX_VALUE);
        Probe cappedProbe = Probe.create(true, Integer.MAX_VALUE);
        OutputHelper.getOutputHandler(capped, RecipeError.NOT_ENOUGH_OUTPUT_SPACE)
              .calculateOperationsCanSupport(cappedProbe.tracker(), water(3));
        assertEquals(Integer.MAX_VALUE / 3, cappedProbe.max(), "the simulate cap keeps the integer division inside the int range");

        FluidTank foreign = new FluidTank(1_000);
        assertTrue(foreign.insert(water(1_000), Action.EXECUTE, AutomationType.INTERNAL).isEmpty(), "the tank starts full");
        foreign.stack = new FluidStack(Fluids.LAVA, 1_000);
        Probe foreignProbe = Probe.create(true, Integer.MAX_VALUE);
        OutputHelper.getOutputHandler(foreign, RecipeError.NOT_ENOUGH_OUTPUT_SPACE)
              .calculateOperationsCanSupport(foreignProbe.tracker(), water(10));
        assertEquals(0, foreignProbe.max());
        assertTrue(foreignProbe.errors().contains(RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              "a full tank of a different fluid reports not enough space");

        FluidTank noRoom = new FluidTank(0);
        Probe noRoomProbe = Probe.create(true, Integer.MAX_VALUE);
        OutputHelper.getOutputHandler(noRoom, RecipeError.NOT_ENOUGH_OUTPUT_SPACE)
              .calculateOperationsCanSupport(noRoomProbe.tracker(), water(10));
        assertEquals(0, noRoomProbe.max());
        assertTrue(noRoomProbe.errors().contains(RecipeError.NOT_ENOUGH_OUTPUT_SPACE),
              "a tank with no capacity and no need reports not enough space");
    }

    @Test
    void itemSlotOutputHandlerMultipliesCountsAndGatesOnSlotLimit() throws Exception {
        ItemSlot slot = new ItemSlot(ItemStack.EMPTY, 64);
        IOutputHandler<ItemStack> handler = OutputHelper.getOutputHandler(slot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
        handler.handleOutput(new ItemStack(Items.IRON_INGOT, 5), 3);
        assertEquals(15, slot.stack.getCount(), "the execute path multiplies the output count by the operations");
        assertEquals(Action.EXECUTE, slot.lastAction);

        ItemSlot noOp = new ItemSlot(ItemStack.EMPTY, 64);
        OutputHelper.getOutputHandler(noOp, RecipeError.NOT_ENOUGH_OUTPUT_SPACE).handleOutput(new ItemStack(Items.IRON_INGOT, 5), 0);
        assertTrue(noOp.stack.isEmpty(), "zero operations must not insert anything");
        OutputHelper.getOutputHandler(noOp, RecipeError.NOT_ENOUGH_OUTPUT_SPACE).handleOutput(ItemStack.EMPTY, 4);
        assertTrue(noOp.stack.isEmpty(), "an empty output must not insert anything even with operations");

        ItemSlot empty = new ItemSlot(ItemStack.EMPTY, 64);
        Probe emptyProbe = Probe.create(true, Integer.MAX_VALUE);
        OutputHelper.getOutputHandler(empty, RecipeError.NOT_ENOUGH_OUTPUT_SPACE)
              .calculateOperationsCanSupport(emptyProbe.tracker(), new ItemStack(Items.IRON_INGOT, 2));
        assertEquals(32, emptyProbe.max(), "sixty-four items of room hold thirty-two two-item outputs");
        assertEquals(Action.SIMULATE, empty.lastAction, "the calculate path must only simulate");
        assertTrue(empty.stack.isEmpty());

        ItemSlot partial = new ItemSlot(new ItemStack(Items.IRON_INGOT, 60), 64);
        Probe partialProbe = Probe.create(true, Integer.MAX_VALUE);
        OutputHelper.getOutputHandler(partial, RecipeError.NOT_ENOUGH_OUTPUT_SPACE)
              .calculateOperationsCanSupport(partialProbe.tracker(), new ItemStack(Items.IRON_INGOT, 2));
        assertEquals(2, partialProbe.max(), "only the four remaining items count as room");

        ItemSlot blocked = new ItemSlot(new ItemStack(Items.DIRT, 1), 64);
        Probe blockedProbe = Probe.create(true, Integer.MAX_VALUE);
        OutputHelper.getOutputHandler(blocked, RecipeError.NOT_ENOUGH_OUTPUT_SPACE)
              .calculateOperationsCanSupport(blockedProbe.tracker(), new ItemStack(Items.IRON_INGOT, 1));
        assertEquals(0, blockedProbe.max());
        assertTrue(blockedProbe.errors().contains(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT),
              "a slot that rejects the item while still having room is reported as the input producing no output");

        ItemSlot full = new ItemSlot(new ItemStack(Items.DIRT, 64), 64);
        Probe fullProbe = Probe.create(true, Integer.MAX_VALUE);
        OutputHelper.getOutputHandler(full, RecipeError.NOT_ENOUGH_OUTPUT_SPACE)
              .calculateOperationsCanSupport(fullProbe.tracker(), new ItemStack(Items.IRON_INGOT, 1));
        assertEquals(0, fullProbe.max());
        assertTrue(fullProbe.errors().contains(RecipeError.NOT_ENOUGH_OUTPUT_SPACE));
        assertFalse(fullProbe.errors().contains(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
    }

    @Test
    void chanceOutputHandlerOrdersMainThenSecondaryAndRefreshesChances() throws Exception {
        List<String> events = new ArrayList<>();
        ItemSlot mainSlot = new ItemSlot(ItemStack.EMPTY, 64);
        ItemSlot secondarySlot = new ItemSlot(ItemStack.EMPTY, 64);
        ChanceProbe output = new ChanceProbe(events);
        IOutputHandler<ChanceOutput> handler = OutputHelper.getOutputHandler(mainSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE, secondarySlot,
              RecipeError.NOT_ENOUGH_SECONDARY_INPUT);
        handler.handleOutput(output, 3);
        assertEquals(List.of("main", "secondary", "next", "next"), events,
              "the handler reads the main output once, the first secondary output once, then refreshes a chance per remaining operation");
        assertEquals(6, mainSlot.stack.getCount(), "the main output is inserted once with the operation multiplier");
        assertEquals(9, secondarySlot.stack.getCount(),
              "the official loop calls the shared item insert with the operation count each iteration, so three operations insert nine nuggets");
        assertEquals(2, output.refreshes, "three operations need two chance refreshes after the first secondary output");
        assertEquals(1, output.mainReads);
        assertEquals(1, output.secondaryReads, "the execute path reads the chance result exactly once");
        assertEquals(0, output.maxSecondaryReads);

        events.clear();
        ItemSlot orderedMain = new ItemSlot(ItemStack.EMPTY, 64);
        ItemSlot orderedSecondary = new ItemSlot(ItemStack.EMPTY, 64);
        ChanceProbe ordered = new ChanceProbe(events);
        Probe orderedProbe = Probe.create(true, 8);
        OutputHelper.getOutputHandler(orderedMain, RecipeError.NOT_ENOUGH_OUTPUT_SPACE, orderedSecondary, RecipeError.NOT_ENOUGH_SECONDARY_INPUT)
              .calculateOperationsCanSupport(orderedProbe.tracker(), ordered);
        assertEquals(List.of("main", "secondary"), events, "the main output is consulted before the secondary one");
        assertEquals(1, ordered.mainReads);
        assertEquals(1, ordered.maxSecondaryReads, "checking room uses the maximum secondary output");
        assertEquals(0, ordered.secondaryReads, "checking room must not roll the chance result");

        ItemSlot emptyMain = new ItemSlot(ItemStack.EMPTY, 64);
        ItemSlot unreachedSecondary = new ItemSlot(ItemStack.EMPTY, 64);
        ChanceProbe emptyMainOutput = new ChanceProbe(new ArrayList<>());
        emptyMainOutput.emptyMain = true;
        Probe shortCircuitProbe = Probe.create(false, 0);
        OutputHelper.getOutputHandler(emptyMain, RecipeError.NOT_ENOUGH_OUTPUT_SPACE, unreachedSecondary, RecipeError.NOT_ENOUGH_SECONDARY_INPUT)
              .calculateOperationsCanSupport(shortCircuitProbe.tracker(), emptyMainOutput);
        assertEquals(0, emptyMainOutput.maxSecondaryReads,
              "an empty main output leaves nothing to continue checking, so the secondary output is never consulted");
    }

    @Test
    void recordOutputHandlersCheckBothMembersInOrderAndShortCircuit() throws Exception {
        List<String> events = new ArrayList<>();
        ItemSlot slot = new ItemSlot(ItemStack.EMPTY, 64);
        RecordingSlot recordingSlot = new RecordingSlot(slot, events);
        RecordingChemicalTank tank = new RecordingChemicalTank(BasicChemicalTank.createAllValid(1_000_000, null), events);
        IOutputHandler<PressurizedReactionRecipeOutput> reaction = OutputHelper.getOutputHandler(recordingSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
              tank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT);
        PressurizedReactionRecipeOutput both = new PressurizedReactionRecipeOutput(new ItemStack(Items.IRON_INGOT, 2), hydrogen(10));
        reaction.handleOutput(both, 3);
        assertEquals(6, slot.stack.getCount(), "the item member is inserted with the operation multiplier");
        assertEquals(30, tank.tank.getStored(), "the chemical member is inserted with the operation multiplier");
        assertEquals(List.of("insertItem", "insertChemical"), events, "the item member is handled before the chemical one");

        events.clear();
        RecordingSlot checkSlot = new RecordingSlot(new ItemSlot(ItemStack.EMPTY, 64), events);
        RecordingChemicalTank checkTank = new RecordingChemicalTank(BasicChemicalTank.createAllValid(1_000_000, null), events);
        OutputHelper.getOutputHandler(checkSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE, checkTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT)
              .calculateOperationsCanSupport(Probe.create(true, 4).tracker(), both);
        assertEquals(List.of("insertItem", "insertChemical"), events, "the item member is checked before the chemical one, both by simulation");
        assertEquals(Action.SIMULATE, checkSlot.lastAction);
        assertEquals(Action.SIMULATE, checkTank.lastAction);

        events.clear();
        RecordingSlot shortCircuitSlot = new RecordingSlot(new ItemSlot(new ItemStack(Items.DIRT, 64), 64), events);
        RecordingChemicalTank collectedTank = new RecordingChemicalTank(BasicChemicalTank.createAllValid(1_000_000, null), events);
        OutputHelper.getOutputHandler(shortCircuitSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE, collectedTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT)
              .calculateOperationsCanSupport(Probe.create(false, 0).tracker(), both);
        assertEquals(List.of("insertItem", "insertChemical"), events,
              "a new error from the item member asks the tracker to keep collecting, so the chemical member is still consulted");

        events.clear();
        RecordingSlot emptyMemberSlot = new RecordingSlot(new ItemSlot(ItemStack.EMPTY, 64), events);
        RecordingChemicalTank emptyMemberTank = new RecordingChemicalTank(BasicChemicalTank.createAllValid(1_000_000, null), events);
        OutputHelper.getOutputHandler(emptyMemberSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE, emptyMemberTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT)
              .calculateOperationsCanSupport(Probe.create(false, 0).tracker(), new PressurizedReactionRecipeOutput(ItemStack.EMPTY, hydrogen(10)));
        assertEquals(List.of(), events,
              "an empty item member adds no error, so a tracker that is not collecting stops before the chemical member");

        events.clear();
        RecordingSlot checkedEmptySlot = new RecordingSlot(new ItemSlot(ItemStack.EMPTY, 64), events);
        RecordingChemicalTank checkedEmptyTank = new RecordingChemicalTank(BasicChemicalTank.createAllValid(1_000_000, null), events);
        OutputHelper.getOutputHandler(checkedEmptySlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE, checkedEmptyTank, RecipeError.NOT_ENOUGH_SECONDARY_INPUT)
              .calculateOperationsCanSupport(Probe.create(true, 0).tracker(), new PressurizedReactionRecipeOutput(ItemStack.EMPTY, hydrogen(10)));
        assertEquals(List.of("insertChemical"), events,
              "a tracker that is collecting all errors skips the empty item member but still checks the chemical one");

        assertThrows(IllegalArgumentException.class, () -> new PressurizedReactionRecipeOutput(ItemStack.EMPTY, ChemicalStack.EMPTY),
              "the record itself rejects two empty members");

        IChemicalTank leftTank = BasicChemicalTank.createAllValid(1_000_000, null);
        IChemicalTank rightTank = BasicChemicalTank.createAllValid(1_000_000, null);
        IOutputHandler<ElectrolysisRecipeOutput> separator = OutputHelper.getOutputHandler(leftTank, RecipeError.NOT_ENOUGH_LEFT_INPUT, rightTank,
              RecipeError.NOT_ENOUGH_RIGHT_INPUT);
        separator.handleOutput(new ElectrolysisRecipeOutput(hydrogen(4), steam(6)), 2);
        assertEquals(8, leftTank.getStored(), "the left member is inserted first");

        FluidTank fluidTank = new FluidTank(1_000_000);
        ItemSlot optionalSlot = new ItemSlot(ItemStack.EMPTY, 64);
        IOutputHandler<FluidOptionalItemOutput> optionalItem = OutputHelper.getOutputHandler(fluidTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE, optionalSlot,
              RecipeError.NOT_ENOUGH_SECONDARY_INPUT);
        optionalItem.handleOutput(new FluidOptionalItemOutput(water(20), ItemStack.EMPTY), 2);
        assertEquals(40, fluidTank.stack.getAmount(), "the fluid member is inserted first");
        assertTrue(optionalSlot.stack.isEmpty(), "an empty optional item member inserts nothing");
        optionalItem.calculateOperationsCanSupport(Probe.create(true, 4).tracker(), new FluidOptionalItemOutput(water(20), new ItemStack(Items.IRON_INGOT, 1)));
        assertEquals(40, fluidTank.stack.getAmount(), "checking the room for an output never inserts it");
    }

    @Test
    void oneInputCachedRecipeKeepsEmptyGuardsAndKeyedHoldersStayNonRegistered() throws Exception {
        Predicate<ElectrolysisRecipeOutput> separatorEmpty = predicate(OneInputCachedRecipe.class, "SEPARATOR_OUTPUT_EMPTY");
        Predicate<FluidOptionalItemOutput> fluidOptionalEmpty = predicate(OneInputCachedRecipe.class, "FLUID_OPTIONAL_ITEM_OUTPUT_EMPTY");
        assertNotSame(separatorEmpty, fluidOptionalEmpty, "the two output-empty predicates must stay distinct instances");
        assertFalse(separatorEmpty.test(new ElectrolysisRecipeOutput(hydrogen(1), steam(1))), "a valid separator output has no empty member");
        assertThrows(IllegalArgumentException.class, () -> new ElectrolysisRecipeOutput(ChemicalStack.EMPTY, steam(1)));
        FluidOptionalItemOutput fluidOnly = new FluidOptionalItemOutput(water(10), ItemStack.EMPTY);
        assertFalse(fluidOptionalEmpty.test(fluidOnly), "the fluid-only predicate ignores the optional item member");
        assertTrue(ConstantPredicates.ITEM_EMPTY.test(fluidOnly.optionalItem()),
              "the same output does have an empty item member, so the two predicates check different members");
        assertThrows(IllegalArgumentException.class, () -> new FluidOptionalItemOutput(FluidStack.EMPTY, ItemStack.EMPTY));

        ItemStackIngredient ingredient = IngredientCreatorAccess.item().from(Items.IRON_INGOT, 1);
        TestItemToFluidRecipe recipe = new TestItemToFluidRecipe(ingredient, water(1_000));
        ItemSlot slot = new ItemSlot(new ItemStack(Items.IRON_INGOT, 10), 64);
        FluidTank tank = new FluidTank(1_000_000);
        OneInputCachedRecipe<ItemStack, FluidStack, ItemStackToFluidRecipe> cached = OneInputCachedRecipe.itemToFluid(recipe, () -> true,
              InputHelper.getInputHandler(slot, RecipeError.NOT_ENOUGH_INPUT), OutputHelper.getOutputHandler(tank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE));
        cached.setBaselineMaxOperations(() -> 10);
        assertNotNull(recipe.getType());
        assertSame(recipe.getType(), recipe.getType(), "getType must be identity stable across calls");
        cached.process();
        assertTrue(slot.stack.isEmpty(), "ten matching inputs are consumed for the ten computed operations");
        assertEquals(10_000, tank.stack.getAmount(), "the finished recipe inserts one thousand units per operation");

        Predicate<Object> sawingOutputCheck = outputEmptyCheck(OneInputCachedRecipe.sawing(new TestSawmillRecipe(ingredient), () -> true,
              InputHelper.getInputHandler(slot, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getOutputHandler(new ItemSlot(ItemStack.EMPTY, 64), RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
                    new ItemSlot(ItemStack.EMPTY, 64), RecipeError.NOT_ENOUGH_SECONDARY_INPUT)));
        assertSame(ConstantPredicates.alwaysFalse(), sawingOutputCheck,
              "sawing keeps the official always-false output check so a chance output is never treated as empty");

        ItemSlot guardedSlot = new ItemSlot(new ItemStack(Items.IRON_INGOT, 10), 64);
        FluidTank guardedTank = new FluidTank(1_000_000);
        OneInputCachedRecipe<ItemStack, FluidStack, ItemStackToFluidRecipe> guarded = OneInputCachedRecipe.itemToFluid(
              new TestItemToFluidRecipe(ingredient, FluidStack.EMPTY), () -> true,
              InputHelper.getInputHandler(guardedSlot, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getOutputHandler(guardedTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE));
        guarded.process();
        assertEquals(10, guardedSlot.stack.getCount(), "the empty-output guard blocks consuming the input");
        assertEquals(0, guardedTank.stack.getAmount(), "the empty-output guard blocks inserting the output");

        ItemSlot nullGuardSlot = new ItemSlot(new ItemStack(Items.IRON_INGOT, 10), 64);
        FluidTank nullGuardTank = new FluidTank(1_000_000);
        OneInputCachedRecipe<ItemStack, FluidStack, ItemStackToFluidRecipe> neverCalculated = OneInputCachedRecipe.itemToFluid(recipe, () -> true,
              InputHelper.getInputHandler(nullGuardSlot, RecipeError.NOT_ENOUGH_INPUT),
              OutputHelper.getOutputHandler(nullGuardTank, RecipeError.NOT_ENOUGH_OUTPUT_SPACE));
        Method finishProcessing = OneInputCachedRecipe.class.getDeclaredMethod("finishProcessing", int.class);
        finishProcessing.setAccessible(true);
        finishProcessing.invoke(neverCalculated, 1);
        assertEquals(10, nullGuardSlot.stack.getCount(), "finishProcessing must guard against a null cached input");
        assertEquals(0, nullGuardTank.stack.getAmount(), "finishProcessing must guard against a null cached output");

        assertNotNull(MekanismRecipeTypes.TYPE_CRUSHING.value());
        assertSame(MekanismRecipeTypes.TYPE_CRUSHING.value(), MekanismRecipeTypes.TYPE_CRUSHING.value());
        assertNotSame(MekanismRecipeTypes.TYPE_CRUSHING.value(), MekanismRecipeTypes.TYPE_ENRICHING.value(),
              "each recipe family keeps its own identity stable instance");
        //Note: Merely touching this class must not write to any registry. Registration is an explicit, idempotent step owned by FabricRecipeRegistration, which
        // runs before the built-in registries are frozen; the isolated fabricRecipeRegistryTest worker asserts the registered outcome.
        assertFalse(MekanismRecipeTypes.isRegistered(), "class initialization alone must not register recipe types");
        for (RecipeType<?> registered : BuiltInRegistries.RECIPE_TYPE) {
            assertNotSame(MekanismRecipeTypes.TYPE_CRUSHING.value(), registered, "class initialization must not register a recipe type");
            assertNotSame(MekanismRecipeTypes.TYPE_SAWING.value(), registered, "class initialization must not register a recipe type");
        }

        assertToastHolder(ChemicalCrystallizerRecipe.class, "CHEMICAL_CRYSTALLIZER", "chemical_crystallizer");
        assertToastHolder(ChemicalDissolutionRecipe.class, "CHEMICAL_DISSOLUTION_CHAMBER", "chemical_dissolution_chamber");
        assertToastHolder(CombinerRecipe.class, "COMBINER", "combiner");
        assertToastHolder(ElectrolysisRecipe.class, "ELECTROLYTIC_SEPARATOR", "electrolytic_separator");
        assertToastHolder(FluidToFluidRecipe.class, "THERMAL_EVAPORATION_CONTROLLER", "thermal_evaporation_controller");
        assertToastHolder(ItemStackToEnergyRecipe.class, "ENERGY_TABLET", "energy_tablet");
        assertToastHolder(NucleosynthesizingRecipe.class, "ANTIPROTONIC_NUCLEOSYNTHESIZER", "antiprotonic_nucleosynthesizer");
        assertToastHolder(PressurizedReactionRecipe.class, "PRESSURIZED_REACTION_CHAMBER", "pressurized_reaction_chamber");
        assertToastHolder(RotaryRecipe.class, "ROTARY_CONDENSENTRATOR", "rotary_condensentrator");
        assertToastHolder(SawmillRecipe.class, "PRECISION_SAWMILL", "precision_sawmill");
    }

    /**
     * The toast items are not admitted, so the keyed holder is deliberately unbound: it must stay non-null, key-correct, and fail closed when a value is asked
     * for. That limitation is asserted here instead of being hidden.
     */
    private static void assertToastHolder(Class<?> owner, String fieldName, String path) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object raw = field.get(null);
        assertInstanceOf(MekanismKeyedHolder.class, raw, owner.getSimpleName() + "." + fieldName + " must be a keyed holder");
        MekanismKeyedHolder<?> holder = (MekanismKeyedHolder<?>) raw;
        assertEquals("mekanism", holder.getKey().location().getNamespace());
        assertEquals(path, holder.getKey().location().getPath());
        assertKeyCorrect(holder);
        assertFalse(holder.isBound(), "the Mekanism item is not registered, so the toast holder is legitimately unbound");
        assertTrue(holder.asOptional().isEmpty());
        assertThrows(IllegalStateException.class, holder::value, "an unbound toast holder must fail closed");
    }

    /**
     * A generic (rather than wildcard) receiver is required so the key argument and the receiver share one type variable.
     */
    private static <T> void assertKeyCorrect(MekanismKeyedHolder<T> holder) {
        assertEquals(holder.getKey(), holder.unwrapKey().orElseThrow());
        assertTrue(holder.is(holder.getKey()));
    }

    @SuppressWarnings("unchecked")
    private static <T> Predicate<T> predicate(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (Predicate<T>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Predicate<Object> outputEmptyCheck(OneInputCachedRecipe<?, ?, ?> cached) throws Exception {
        Field field = OneInputCachedRecipe.class.getDeclaredField("outputEmptyCheck");
        field.setAccessible(true);
        return (Predicate<Object>) field.get(cached);
    }

    private static ChemicalStack hydrogen(long amount) {
        return new ChemicalStack(MekanismChemicals.HYDROGEN, amount);
    }

    private static ChemicalStack steam(long amount) {
        return new ChemicalStack(MekanismChemicals.STEAM, amount);
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    /** Real tracker handle built through the same private constructor the cached recipe uses. */
    private static final class Probe {
        private final OperationTracker tracker;
        private final Field max;
        private final Field errors;

        private Probe(OperationTracker tracker, Field max, Field errors) {
            this.tracker = tracker;
            this.max = max;
            this.errors = errors;
        }

        static Probe create(boolean checkAll, int startingMax) throws Exception {
            Constructor<OperationTracker> constructor = OperationTracker.class.getDeclaredConstructor(Set.class, boolean.class, int.class);
            constructor.setAccessible(true);
            Field max = OperationTracker.class.getDeclaredField("currentMax");
            max.setAccessible(true);
            Field errors = OperationTracker.class.getDeclaredField("errors");
            errors.setAccessible(true);
            return new Probe(constructor.newInstance(Set.of(), checkAll, startingMax), max, errors);
        }

        OperationTracker tracker() {
            return tracker;
        }

        int max() throws Exception {
            return max.getInt(tracker);
        }

        @SuppressWarnings("unchecked")
        Set<RecipeError> errors() throws Exception {
            return (Set<RecipeError>) errors.get(tracker);
        }
    }

    private static final class ItemSlot implements IInventorySlot {
        private ItemStack stack;
        private final int limit;
        private Action lastAction;

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
        public ItemStack insertItem(ItemStack stack, Action action, AutomationType automationType) {
            lastAction = action;
            return IInventorySlot.super.insertItem(stack, action, automationType);
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

    /** Delegating slot that records when the handler asks it to insert something. */
    private static final class RecordingSlot implements IInventorySlot {
        private final ItemSlot slot;
        private final List<String> events;
        private Action lastAction;

        private RecordingSlot(ItemSlot slot, List<String> events) {
            this.slot = slot;
            this.events = events;
        }

        @Override
        public ItemStack insertItem(ItemStack stack, Action action, AutomationType automationType) {
            events.add("insertItem");
            lastAction = action;
            return slot.insertItem(stack, action, automationType);
        }

        @Override
        public ItemStack getStack() {
            return slot.getStack();
        }

        @Override
        public void setStack(ItemStack stack) {
            slot.setStack(stack);
        }

        @Override
        public int getLimit(ItemStack stack) {
            return slot.getLimit(stack);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return slot.isItemValid(stack);
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

    /** Delegating chemical tank that records when the handler asks it to insert something. */
    private static final class RecordingChemicalTank implements IChemicalTank {
        private final IChemicalTank tank;
        private final List<String> events;
        private Action lastAction;

        private RecordingChemicalTank(IChemicalTank tank, List<String> events) {
            this.tank = tank;
            this.events = events;
        }

        @Override
        public ChemicalStack insert(ChemicalStack stack, Action action, AutomationType automationType) {
            events.add("insertChemical");
            lastAction = action;
            return tank.insert(stack, action, automationType);
        }

        @Override
        public ChemicalStack getStack() {
            return tank.getStack();
        }

        @Override
        public void setStack(ChemicalStack stack) {
            tank.setStack(stack);
        }

        @Override
        public void setStackUnchecked(ChemicalStack stack) {
            tank.setStackUnchecked(stack);
        }

        @Override
        public long getCapacity() {
            return tank.getCapacity();
        }

        @Override
        public boolean isValid(ChemicalStack stack) {
            return tank.isValid(stack);
        }

        @Override
        public void onContentsChanged() {
        }
    }

    private static final class FluidTank implements IExtendedFluidTank {
        private FluidStack stack = FluidStack.EMPTY;
        private final int capacity;
        private Action lastAction;


        private FluidTank(int capacity) {
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
        public FluidStack insert(FluidStack stack, Action action, AutomationType automationType) {
            lastAction = action;
            return IExtendedFluidTank.super.insert(stack, action, automationType);
        }

        @Override
        public void onContentsChanged() {
        }
    }

    /** Chance output stub that records how the handler drives it. Each accessor records exactly one event. */
    private static final class ChanceProbe implements ChanceOutput {
        private final List<String> events;
        private int refreshes;
        private int mainReads;
        private int secondaryReads;
        private int maxSecondaryReads;
        private boolean emptyMain;

        private ChanceProbe(List<String> events) {
            this.events = events;
        }

        @Override
        public ItemStack getMainOutput() {
            events.add("main");
            mainReads++;
            return emptyMain ? ItemStack.EMPTY : new ItemStack(Items.GOLD_INGOT, 2);
        }

        @Override
        public ItemStack getMaxSecondaryOutput() {
            events.add("secondary");
            maxSecondaryReads++;
            return new ItemStack(Items.GOLD_NUGGET, 1);
        }

        @Override
        public ItemStack getSecondaryOutput() {
            events.add("secondary");
            secondaryReads++;
            return new ItemStack(Items.GOLD_NUGGET, 1);
        }

        @Override
        public ItemStack nextSecondaryOutput() {
            refreshes++;
            events.add("next");
            return new ItemStack(Items.GOLD_NUGGET, 1);
        }
    }

    /** Minimal ItemStack to fluid recipe fixture with a fixed output. */
    private static final class TestItemToFluidRecipe extends ItemStackToFluidRecipe {
        private final ItemStackIngredient input;
        private final FluidStack output;

        private TestItemToFluidRecipe(ItemStackIngredient input, FluidStack output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public boolean test(ItemStack itemStack) {
            return input.test(itemStack);
        }

        @Override
        public ItemStackIngredient getInput() {
            return input;
        }

        @Override
        public FluidStack getOutput(ItemStack input) {
            return output;
        }

        @Override
        public List<FluidStack> getOutputDefinition() {
            return List.of(output);
        }

        @Override
        public RecipeType<?> getType() {
            return MekanismRecipeTypes.TYPE_EVAPORATING.value();
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return SERIALIZER;
        }

        private static final RecipeSerializer<TestItemToFluidRecipe> SERIALIZER = new RecipeSerializer<>() {
            @Override
            public com.mojang.serialization.MapCodec<TestItemToFluidRecipe> codec() {
                throw new UnsupportedOperationException("Fixture serializer has no codec");
            }

            @Override
            public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, TestItemToFluidRecipe> streamCodec() {
                throw new UnsupportedOperationException("Fixture serializer has no stream codec");
            }
        };
    }

    /** Minimal sawmill recipe fixture used to read the cached recipe's output-empty predicate. */
    private static final class TestSawmillRecipe extends SawmillRecipe {
        private final ItemStackIngredient input;

        private TestSawmillRecipe(ItemStackIngredient input) {
            this.input = input;
        }

        @Override
        public boolean test(ItemStack stack) {
            return input.test(stack);
        }

        @Override
        public ChanceOutput getOutput(ItemStack input) {
            throw new UnsupportedOperationException("Fixture sawmill recipe is never executed");
        }

        @Override
        public List<ItemStack> getMainOutputDefinition() {
            return List.of();
        }

        @Override
        public List<ItemStack> getSecondaryOutputDefinition() {
            return List.of();
        }

        @Override
        public double getSecondaryChance() {
            return 0;
        }

        @Override
        public ItemStackIngredient getInput() {
            return input;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return SERIALIZER;
        }

        private static final RecipeSerializer<TestSawmillRecipe> SERIALIZER = new RecipeSerializer<>() {
            @Override
            public com.mojang.serialization.MapCodec<TestSawmillRecipe> codec() {
                throw new UnsupportedOperationException("Fixture serializer has no codec");
            }

            @Override
            public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, TestSawmillRecipe> streamCodec() {
                throw new UnsupportedOperationException("Fixture serializer has no stream codec");
            }
        };
    }
}
