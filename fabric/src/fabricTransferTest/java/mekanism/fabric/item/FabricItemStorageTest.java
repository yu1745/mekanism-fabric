package mekanism.fabric.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.common.capabilities.holder.IHolder;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FabricItemStorageTest {

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exposureViewsPreserveVariantComponentsBlankZeroCapacityAndRejectInvalidBoundaries() {
        ItemStack namedStack = new ItemStack(Items.DIAMOND, 7);
        namedStack.set(DataComponents.CUSTOM_NAME, Component.literal("component-identity"));
        TestSlot named = new TestSlot(64, namedStack);
        TestSlot empty = new TestSlot(777, ItemStack.EMPTY);
        MutableInventory inventory = new MutableInventory(List.of(named, empty));
        FabricItemStorage storage = FabricItemLookup.expose(inventory, Direction.NORTH, null);
        assertNotNull(storage);
        assertEquals(2, storage.getSlotCount());
        assertSame(storage.getSlot(0), storage.getSlots().get(0));
        assertSame(storage.getSlot(1), storage.getSlots().get(1));

        ItemVariant namedVariant = ItemVariant.of(namedStack);
        SingleSlotStorage<ItemVariant> namedView = storage.getSlot(0);
        assertEquals(namedVariant, namedView.getResource());
        assertEquals(namedStack.getComponentsPatch(), namedView.getResource().getComponents());
        assertEquals(7, namedView.getAmount());
        assertEquals(64, namedView.getCapacity());
        assertFalse(namedView.isResourceBlank());
        SingleSlotStorage<ItemVariant> emptyView = storage.getSlot(1);
        assertTrue(emptyView.isResourceBlank());
        assertEquals(ItemVariant.blank(), emptyView.getResource());
        assertEquals(0, emptyView.getAmount());
        assertEquals(777, emptyView.getCapacity());
        assertTrue(empty.sawEmptyCapacityQuery);

        Iterator<StorageView<ItemVariant>> iterator = storage.iterator();
        assertSame(namedView, iterator.next());
        assertSame(emptyView, iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        assertThrows(IndexOutOfBoundsException.class, () -> storage.getSlot(2));

        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, storage.insert(ItemVariant.blank(), Long.MAX_VALUE, transaction));
            assertEquals(0, storage.extract(ItemVariant.blank(), Long.MAX_VALUE, transaction));
            assertEquals(0, storage.insert(namedVariant, 0, transaction));
            assertEquals(0, storage.extract(namedVariant, 0, transaction));
            assertThrows(IllegalArgumentException.class, () -> storage.insert(namedVariant, -1, transaction));
            assertThrows(IllegalArgumentException.class, () -> namedView.extract(namedVariant, -1, transaction));
            transaction.commit();
        }
        assertEquals(0, named.simulations + named.executions + empty.simulations + empty.executions);
        assertEquals(7, named.getStack().getCount());

        assertEquals(null, FabricItemLookup.expose(null, Direction.NORTH, null));
        assertEquals(null, FabricItemLookup.expose(inventory, null, null));
        MutableInventory disabled = new MutableInventory(List.of(named));
        disabled.hasInventory = false;
        assertEquals(null, FabricItemLookup.expose(disabled, Direction.NORTH, null));
        assertEquals(null, FabricItemLookup.expose(new MutableInventory(List.of()), Direction.NORTH, null));
        MutableInventory northOnly = new MutableInventory(List.of(named));
        northOnly.onlySide = Direction.NORTH;
        assertEquals(null, FabricItemLookup.expose(northOnly, Direction.SOUTH, null));
    }

    @Test
    void longMultiSlotTransfersUseStableOrderNestedCommitAndOuterAbortWithObservableRestore() {
        ItemStack componentStack = new ItemStack(Items.EMERALD, 1);
        componentStack.set(DataComponents.CUSTOM_NAME, Component.literal("long-transfer"));
        ItemVariant variant = ItemVariant.of(componentStack);
        TestSlot first = new TestSlot(Integer.MAX_VALUE, ItemStack.EMPTY);
        TestSlot second = new TestSlot(Integer.MAX_VALUE, ItemStack.EMPTY);
        TestSlot third = new TestSlot(Integer.MAX_VALUE, ItemStack.EMPTY);
        FabricItemStorage storage = FabricItemLookup.expose(
              new MutableInventory(List.of(first, second, third)), Direction.WEST, null);
        assertNotNull(storage);
        long amount = 2L * Integer.MAX_VALUE + 17L;

        try (Transaction outer = Transaction.openOuter()) {
            assertEquals(amount, storage.insert(variant, amount, outer));
            assertEquals(Integer.MAX_VALUE, first.getCount());
            assertEquals(Integer.MAX_VALUE, second.getCount());
            assertEquals(17, third.getCount());
            assertEquals(variant, storage.getSlot(0).getResource());
            assertEquals(componentStack.getComponentsPatch(), first.getStack().getComponentsPatch());
            outer.commit();
        }
        assertEquals(List.of(Action.SIMULATE, Action.EXECUTE), first.actions);
        assertEquals(List.of(Action.SIMULATE, Action.EXECUTE), second.actions);
        assertEquals(List.of(Action.SIMULATE, Action.EXECUTE), third.actions);

        int notificationsBeforeAbort = first.notifications + second.notifications + third.notifications;
        try (Transaction outer = Transaction.openOuter()) {
            try (Transaction nested = outer.openNested()) {
                assertEquals(amount, storage.extract(variant, Long.MAX_VALUE, nested));
                assertEquals(0, first.getCount());
                assertEquals(0, second.getCount());
                assertEquals(0, third.getCount());
                nested.commit();
            }
            // Deliberately abort the outer transaction after the committed child and adapter children.
        }
        assertEquals(Integer.MAX_VALUE, first.getCount());
        assertEquals(Integer.MAX_VALUE, second.getCount());
        assertEquals(17, third.getCount());
        assertTrue(first.notifications + second.notifications + third.notifications > notificationsBeforeAbort);

        try (Transaction outer = Transaction.openOuter()) {
            assertEquals(amount, storage.extract(variant, amount, outer));
            outer.commit();
        }
        assertEquals(0, first.getCount());
        assertEquals(0, second.getCount());
        assertEquals(0, third.getCount());
        assertEquals(2L * Integer.MAX_VALUE + 17L, amount);
    }

    @Test
    void executionMismatchRestoresEveryCopyAndRestoreFailureIsAggregatedAfterAllAttempts() {
        ItemStack firstStack = new ItemStack(Items.GOLD_INGOT, 4);
        firstStack.set(DataComponents.CUSTOM_NAME, Component.literal("first-original"));
        ItemStack secondStack = new ItemStack(Items.IRON_INGOT, 6);
        secondStack.set(DataComponents.CUSTOM_NAME, Component.literal("second-original"));
        TestSlot first = new TestSlot(64, firstStack);
        TestSlot second = new TestSlot(64, secondStack);
        first.mismatchInsertionExecution = true;
        first.afterExecution = () -> second.directSet(new ItemStack(Items.DIRT, 3));
        FabricItemStorage mismatch = FabricItemLookup.expose(
              new MutableInventory(List.of(first, second)), Direction.UP, null);
        assertNotNull(mismatch);
        ItemStack offered = new ItemStack(Items.GOLD_INGOT, 5);
        offered.set(DataComponents.CUSTOM_NAME, Component.literal("first-original"));
        ItemVariant offeredVariant = ItemVariant.of(offered);

        int notificationsBefore = first.notifications + second.notifications;
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, mismatch.insert(offeredVariant, 5, transaction));
            transaction.commit();
        }
        assertStack(firstStack, first.getStack());
        assertStack(secondStack, second.getStack());
        assertTrue(first.notifications + second.notifications > notificationsBefore);
        assertTrue(first.setAttempts >= 1);
        assertTrue(second.setAttempts >= 1);
        assertEquals(List.of(Action.SIMULATE, Action.EXECUTE), first.actions);

        TestSlot cannotRestore = new TestSlot(64, ItemStack.EMPTY);
        TestSlot restoredLater = new TestSlot(64, ItemStack.EMPTY);
        FabricItemStorage failing = FabricItemLookup.expose(
              new MutableInventory(List.of(cannotRestore, restoredLater)), Direction.DOWN, null);
        assertNotNull(failing);
        Transaction outer = Transaction.openOuter();
        assertEquals(70, failing.insert(ItemVariant.of(Items.COAL), 70, outer));
        assertEquals(64, cannotRestore.getCount());
        assertEquals(6, restoredLater.getCount());
        cannotRestore.failSet = true;
        RuntimeException closeFailure = assertThrows(RuntimeException.class, outer::close);
        assertEquals(64, cannotRestore.getCount());
        assertEquals(0, restoredLater.getCount());
        assertTrue(cannotRestore.setAttempts >= 1);
        assertTrue(restoredLater.setAttempts >= 1);
        Throwable aggregate = closeFailure.getCause();
        assertNotNull(aggregate);
        Throwable primary = aggregate.getCause();
        assertNotNull(primary);
        assertTrue(primary.getMessage().contains("restore slot"));
        assertTrue(primary.getSuppressed().length >= 1);
        assertTrue(primary.getSuppressed()[0].getMessage().contains("restored"));
    }

    @Test
    void livePolicyInvalidationAndTopologyDriftFailClosedBeforeAndDuringOperations() {
        TestSlot slot = new TestSlot(64, new ItemStack(Items.REDSTONE, 8));
        MutableInventory inventory = new MutableInventory(List.of(slot));
        MutablePolicy policy = new MutablePolicy();
        FabricItemStorage storage = FabricItemLookup.expose(inventory, Direction.SOUTH, policy);
        assertNotNull(storage);
        SingleSlotStorage<ItemVariant> retainedView = storage.getSlot(0);
        ItemVariant redstone = ItemVariant.of(Items.REDSTONE);
        assertTrue(storage.supportsInsertion());
        assertTrue(storage.supportsExtraction());

        policy.insert = false;
        assertFalse(storage.supportsInsertion());
        assertTrue(storage.supportsExtraction());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, storage.insert(redstone, 4, transaction));
            transaction.commit();
        }
        assertEquals(8, slot.getCount());
        policy.insert = true;
        policy.extract = false;
        assertTrue(storage.supportsInsertion());
        assertFalse(retainedView.supportsExtraction());
        policy.extract = true;
        assertTrue(retainedView.supportsExtraction());
        assertTrue(policy.seenSides.stream().allMatch(side -> side == Direction.SOUTH));

        TestSlot simulationOnly = new TestSlot(64, ItemStack.EMPTY);
        MutablePolicy closesAfterSimulation = new MutablePolicy();
        simulationOnly.afterSimulation = () -> closesAfterSimulation.insert = false;
        FabricItemStorage simulationStorage = FabricItemLookup.expose(
              new MutableInventory(List.of(simulationOnly)), Direction.WEST, closesAfterSimulation);
        assertNotNull(simulationStorage);
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, simulationStorage.insert(ItemVariant.of(new ItemStack(Items.COPPER_INGOT).copyWithCount(1)), 5, transaction));
            transaction.commit();
        }
        assertEquals(1, simulationOnly.simulations);
        assertEquals(0, simulationOnly.executions);
        assertEquals(0, simulationOnly.notifications);
        assertEquals(0, simulationOnly.getCount());

        storage.invalidate();
        assertFalse(storage.supportsInsertion());
        assertFalse(storage.supportsExtraction());
        assertFalse(retainedView.supportsInsertion());
        assertTrue(retainedView.isResourceBlank());
        assertEquals(ItemVariant.blank(), retainedView.getResource());
        assertEquals(0, retainedView.getAmount());
        assertEquals(0, retainedView.getCapacity());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, storage.extract(redstone, 8, transaction));
            assertEquals(0, retainedView.insert(redstone, 8, transaction));
            transaction.commit();
        }
        assertEquals(8, slot.getCount());

        TestSlot driftSlot = new TestSlot(64, new ItemStack(Items.LAPIS_LAZULI, 9));
        MutableInventory driftInventory = new MutableInventory(List.of(driftSlot));
        FabricItemStorage drifted = FabricItemLookup.expose(driftInventory, Direction.NORTH, null);
        assertNotNull(drifted);
        SingleSlotStorage<ItemVariant> driftView = drifted.getSlot(0);
        driftInventory.slots = List.of(new TestSlot(64, ItemStack.EMPTY));
        assertFalse(drifted.supportsExtraction());
        assertTrue(driftView.isResourceBlank());
        assertEquals(0, driftView.getAmount());
        driftInventory.slots = List.of(driftSlot);
        assertFalse(drifted.supportsExtraction());

        TestSlot during = new TestSlot(64, ItemStack.EMPTY);
        MutableInventory duringInventory = new MutableInventory(List.of(during));
        FabricItemStorage duringStorage = FabricItemLookup.expose(duringInventory, Direction.EAST, null);
        assertNotNull(duringStorage);
        during.afterExecution = () -> duringInventory.slots = List.of(new TestSlot(64, ItemStack.EMPTY));
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, duringStorage.insert(ItemVariant.of(Items.QUARTZ), 5, transaction));
            transaction.commit();
        }
        assertEquals(0, during.getCount());
        assertFalse(duringStorage.supportsInsertion());
        assertTrue(duringStorage.getSlot(0).isResourceBlank());
    }

    @Test
    void realBasicInputAndOutputSlotsParticipateInExposureCommitAndRollback() {
        BasicInventorySlot basic = BasicInventorySlot.at(stack -> stack.is(Items.DIAMOND), null, 0, 0);
        InputInventorySlot input = InputInventorySlot.at(stack -> stack.is(Items.IRON_INGOT), null, 0, 0);
        OutputInventorySlot output = OutputInventorySlot.at(null, 0, 0);
        output.setStack(new ItemStack(Items.GOLD_INGOT, 4));
        FabricItemStorage storage = FabricItemLookup.expose(
              new MutableInventory(List.of(basic, input, output)), Direction.NORTH, null);
        assertNotNull(storage);

        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(5, storage.insert(ItemVariant.of(Items.IRON_INGOT), 5, transaction));
            assertEquals(5, input.getCount());
            transaction.commit();
        }
        assertEquals(5, input.getCount());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(3, storage.insert(ItemVariant.of(Items.DIAMOND), 3, transaction));
            assertEquals(3, basic.getCount());
            assertEquals(4, storage.extract(ItemVariant.of(Items.GOLD_INGOT), 4, transaction));
            assertTrue(output.isEmpty());
            // Abort: all three real slot implementations must be restored through setStack.
        }
        assertTrue(basic.isEmpty());
        assertEquals(5, input.getCount());
        assertEquals(4, output.getCount());
        assertTrue(output.getStack().is(Items.GOLD_INGOT));
    }

    private static void assertStack(ItemStack expected, ItemStack actual) {
        assertEquals(expected.getCount(), actual.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(expected, actual));
    }

    private static final class MutablePolicy implements IHolder {
        private final List<Direction> seenSides = new ArrayList<>();
        private boolean insert = true;
        private boolean extract = true;

        @Override
        public boolean canInsert(@Nullable Direction direction) {
            seenSides.add(direction);
            return insert;
        }

        @Override
        public boolean canExtract(@Nullable Direction direction) {
            seenSides.add(direction);
            return extract;
        }
    }

    private static final class MutableInventory implements IMekanismInventory {
        private List<IInventorySlot> slots;
        private boolean hasInventory = true;
        @Nullable
        private Direction onlySide;

        private MutableInventory(List<? extends IInventorySlot> slots) {
            this.slots = new ArrayList<>(slots);
        }

        @Override
        public boolean hasInventory() {
            return hasInventory;
        }

        @Override
        public List<IInventorySlot> getInventorySlots(@Nullable Direction side) {
            boolean physical = side == Direction.NORTH || side == Direction.SOUTH || side == Direction.EAST
                  || side == Direction.WEST || side == Direction.UP || side == Direction.DOWN;
            return physical && (onlySide == null || onlySide == side) ? slots : List.of();
        }

        @Override
        public void onContentsChanged() {
        }
    }

    private static final class TestSlot implements IInventorySlot {
        private final int limit;
        private final List<Action> actions = new ArrayList<>();
        private ItemStack stack;
        private int simulations;
        private int executions;
        private int notifications;
        private int setAttempts;
        private boolean sawEmptyCapacityQuery;
        private boolean mismatchInsertionExecution;
        private boolean failSet;
        @Nullable
        private Runnable afterExecution;
        @Nullable
        private Runnable afterSimulation;

        private TestSlot(int limit, ItemStack initial) {
            this.limit = limit;
            stack = copy(initial);
        }

        @Override
        public ItemStack getStack() {
            return stack;
        }

        @Override
        public void setStack(ItemStack stack) {
            setAttempts++;
            if (failSet) {
                throw new UnsupportedOperationException("cannot restore slot");
            }
            directSet(stack);
        }

        private void directSet(ItemStack stack) {
            ItemStack copy = copy(stack);
            if (this.stack.getCount() != copy.getCount()
                  || !ItemStack.isSameItemSameComponents(this.stack, copy)) {
                this.stack = copy;
                onContentsChanged();
            }
        }

        @Override
        public ItemStack insertItem(ItemStack offered, Action action, AutomationType automationType) {
            actions.add(action);
            if (action.simulate()) {
                simulations++;
                if (afterSimulation != null) {
                    afterSimulation.run();
                }
            } else {
                executions++;
            }
            if (offered.isEmpty() || (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, offered))) {
                return offered;
            }
            int room = limit - getCount();
            int accepted = Math.min(Math.max(0, room), offered.getCount());
            if (action.execute() && mismatchInsertionExecution && accepted > 0) {
                accepted--;
            }
            if (action.execute() && accepted > 0) {
                if (stack.isEmpty()) {
                    directSet(offered.copyWithCount(accepted));
                } else {
                    directSet(stack.copyWithCount(stack.getCount() + accepted));
                }
                if (afterExecution != null) {
                    afterExecution.run();
                }
            }
            return offered.copyWithCount(offered.getCount() - accepted);
        }

        @Override
        public ItemStack extractItem(int amount, Action action, AutomationType automationType) {
            actions.add(action);
            if (action.simulate()) {
                simulations++;
            } else {
                executions++;
            }
            if (stack.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int extracted = Math.min(amount, stack.getCount());
            ItemStack result = stack.copyWithCount(extracted);
            if (action.execute()) {
                int remaining = stack.getCount() - extracted;
                directSet(remaining == 0 ? ItemStack.EMPTY : stack.copyWithCount(remaining));
                if (afterExecution != null) {
                    afterExecution.run();
                }
            }
            return result;
        }

        @Override
        public int getLimit(ItemStack stack) {
            if (stack.isEmpty()) {
                sawEmptyCapacityQuery = true;
            }
            return limit;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return true;
        }

        @Override
        public void onContentsChanged() {
            notifications++;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        }

        private static ItemStack copy(ItemStack stack) {
            return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
    }
}
