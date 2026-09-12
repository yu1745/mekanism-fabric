package mekanism.common.capabilities.holder.slot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.RelativeSide;
import mekanism.api.SerializationConstants;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InventoryApiHolderTest {

    private static HolderLookup.Provider provider;

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void oversizedSerializationKeepsCountAndComponentsAndRejectsInconsistentEmptyStack() {
        ItemStack oversized = new ItemStack(Items.DIAMOND, 500);
        Component name = Component.literal("task033-oversized");
        oversized.set(DataComponents.CUSTOM_NAME, name);
        TrackingSlot slot = new TrackingSlot(1_000);
        slot.setStack(oversized);

        CompoundTag serialized = slot.serializeNBT(provider);
        CompoundTag item = serialized.getCompound(SerializationConstants.ITEM);
        assertEquals("minecraft:diamond", item.getString(SerializationConstants.ID));
        assertEquals(500, item.getInt(SerializationConstants.COUNT));
        assertTrue(item.getCompound(SerializationConstants.COMPONENTS).contains("minecraft:custom_name"));
        assertEquals(500, slot.getCount());
        assertEquals(name, slot.getStack().get(DataComponents.CUSTOM_NAME));

        TrackingSlot inconsistent = new TrackingSlot(64);
        inconsistent.forceNonEmpty = true;
        IllegalStateException failure = assertThrows(IllegalStateException.class,
              () -> inconsistent.serializeNBT(provider));
        assertEquals("Cannot encode empty ItemStack", failure.getMessage());
    }

    @Test
    void inventorySlotDefaultsPreserveCallerStacksLimitsSimulationAndSignedGrowShrink() {
        TrackingSlot slot = new TrackingSlot(20);
        ItemStack caller = new ItemStack(Items.IRON_INGOT, 15);
        caller.set(DataComponents.CUSTOM_NAME, Component.literal("caller-owned"));
        ItemStack simulatedRemainder = slot.insertItem(caller, Action.SIMULATE, AutomationType.EXTERNAL);
        assertEquals(0, slot.getCount());
        assertEquals(15, caller.getCount());
        assertEquals(0, slot.listenerCalls);
        assertEquals(0, simulatedRemainder.getCount());

        ItemStack executedRemainder = slot.insertItem(caller, Action.EXECUTE, AutomationType.EXTERNAL);
        assertEquals(15, slot.getCount());
        assertEquals(15, caller.getCount());
        assertNotSame(caller, slot.getStack());
        assertEquals(caller.getComponentsPatch(), slot.getStack().getComponentsPatch());
        assertEquals(0, executedRemainder.getCount());
        assertEquals(1, slot.listenerCalls);

        assertEquals(-3, slot.growStack(-3, Action.SIMULATE));
        assertEquals(15, slot.getCount());
        assertEquals(-3, slot.growStack(-3, Action.EXECUTE));
        assertEquals(12, slot.getCount());
        assertEquals(-5, slot.shrinkStack(-5, Action.EXECUTE));
        assertEquals(17, slot.getCount());
        assertEquals(3, slot.growStack(100, Action.EXECUTE));
        assertEquals(20, slot.getCount());
        assertEquals(0, slot.growStack(1, Action.EXECUTE));
        assertEquals(20, slot.getLimit(ItemStack.EMPTY));
    }

    @Test
    void sidedCompatibilityRoutesExactSideActionAutomationAndInvalidIndexDefaults() {
        TrackingSlot slot = new TrackingSlot(64);
        TestInventory inventory = new TestInventory(Direction.EAST, true, new ArrayList<>(List.of(slot)));
        ItemStack caller = new ItemStack(Items.GOLD_INGOT, 10);

        assertEquals(1, inventory.getSlots());
        assertTrue(inventory.getStackInSlot(0).isEmpty());
        assertEquals(0, inventory.insertItem(0, caller, true).getCount());
        assertEquals(0, slot.getCount());
        assertEquals(Action.SIMULATE, slot.actions.getLast());
        assertEquals(AutomationType.EXTERNAL, slot.automationTypes.getLast());
        assertEquals(0, inventory.insertItem(0, caller, false).getCount());
        assertEquals(10, slot.getCount());
        assertEquals(Action.EXECUTE, slot.actions.getLast());
        assertEquals(AutomationType.EXTERNAL, slot.automationTypes.getLast());
        assertEquals(4, inventory.extractItem(0, 4, true).getCount());
        assertEquals(10, slot.getCount());
        assertEquals(Action.SIMULATE, slot.actions.getLast());
        assertEquals(4, inventory.extractItem(0, 4, false).getCount());
        assertEquals(6, slot.getCount());
        assertEquals(Action.EXECUTE, slot.actions.getLast());
        assertTrue(inventory.seenSides.stream().allMatch(side -> side == Direction.EAST));

        inventory.insertItem(0, new ItemStack(Items.GOLD_INGOT), null, Action.SIMULATE);
        assertEquals(AutomationType.INTERNAL, slot.automationTypes.getLast());
        ItemStack invalidCaller = new ItemStack(Items.COAL, 3);
        assertSame(invalidCaller, inventory.insertItem(-1, invalidCaller, Direction.EAST, Action.EXECUTE));
        assertTrue(inventory.extractItem(2, 9, Direction.EAST, Action.EXECUTE).isEmpty());
        assertTrue(inventory.getStackInSlot(2, Direction.EAST).isEmpty());
        assertEquals(0, inventory.getSlotLimit(2, Direction.EAST));
        assertFalse(inventory.isItemValid(2, invalidCaller, Direction.EAST));
        inventory.setStackInSlot(2, invalidCaller, Direction.EAST);
        assertEquals(6, slot.getCount());

        TestInventory disabled = new TestInventory(Direction.EAST, false, new ArrayList<>(List.of(slot)));
        assertFalse(disabled.hasInventory());
        assertSame(disabled.slots, disabled.getInventorySlots(Direction.EAST));
        assertTrue(disabled.getInventorySlots(Direction.EAST).isEmpty());
        assertEquals(0, disabled.getSlots(Direction.EAST));
    }

    @Test
    void inventoryHolderDistinguishesInternalAndPhysicalViewsWithLiveFacingPolicyAndIdentity() {
        AtomicReference<Direction> facing = new AtomicReference<>(Direction.NORTH);
        InventorySlotHolder holder = new InventorySlotHolder(facing::get,
              side -> side == RelativeSide.FRONT, side -> side == RelativeSide.LEFT);
        TrackingSlot front = new TrackingSlot(64);
        TrackingSlot left = new TrackingSlot(64);
        TrackingSlot internalOnly = new TrackingSlot(64);
        holder.addSlot(front, RelativeSide.FRONT);
        holder.addSlot(left, RelativeSide.LEFT);
        holder.addSlot(internalOnly);

        List<IInventorySlot> internal = holder.getInventorySlots(null);
        List<IInventorySlot> north = holder.getInventorySlots(Direction.NORTH);
        List<IInventorySlot> east = holder.getInventorySlots(Direction.EAST);
        assertEquals(List.of(front, left, internalOnly), internal);
        assertEquals(List.of(front), north);
        assertEquals(List.of(left), east);
        assertSame(internal, holder.getInventorySlots(null));
        assertSame(north, holder.getInventorySlots(Direction.NORTH));
        assertNotSame(internal, north);
        assertFalse(holder.canInsert(null));
        assertFalse(holder.canExtract(null));
        assertTrue(holder.canInsert(Direction.NORTH));
        assertFalse(holder.canExtract(Direction.NORTH));
        assertFalse(holder.canInsert(Direction.EAST));
        assertTrue(holder.canExtract(Direction.EAST));

        facing.set(Direction.EAST);
        assertSame(north, holder.getInventorySlots(Direction.EAST));
        assertSame(east, holder.getInventorySlots(Direction.SOUTH));
        assertTrue(holder.canInsert(Direction.EAST));
        assertTrue(holder.canExtract(Direction.SOUTH));
        north.clear();
        assertTrue(holder.getInventorySlots(Direction.EAST).isEmpty());
        assertEquals(List.of(front, left, internalOnly), holder.getInventorySlots(null));
    }

    private static final class TrackingSlot implements IInventorySlot {
        private final int limit;
        private final List<Action> actions = new ArrayList<>();
        private final List<AutomationType> automationTypes = new ArrayList<>();
        private ItemStack stack = ItemStack.EMPTY;
        private int listenerCalls;
        private boolean forceNonEmpty;

        private TrackingSlot(int limit) {
            this.limit = limit;
        }

        @Override
        public ItemStack getStack() {
            return stack;
        }

        @Override
        public void setStack(ItemStack stack) {
            ItemStack copy = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            if (this.stack.getCount() != copy.getCount()
                  || !ItemStack.isSameItemSameComponents(this.stack, copy)) {
                this.stack = copy;
                onContentsChanged();
            }
        }

        @Override
        public ItemStack insertItem(ItemStack stack, Action action, AutomationType automationType) {
            actions.add(action);
            automationTypes.add(automationType);
            return IInventorySlot.super.insertItem(stack, action, automationType);
        }

        @Override
        public ItemStack extractItem(int amount, Action action, AutomationType automationType) {
            actions.add(action);
            automationTypes.add(automationType);
            return IInventorySlot.super.extractItem(amount, action, automationType);
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
        public boolean isEmpty() {
            return !forceNonEmpty && IInventorySlot.super.isEmpty();
        }

        @Override
        public void onContentsChanged() {
            listenerCalls++;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        }
    }

    private static final class TestInventory implements IMekanismInventory {
        private final Direction defaultSide;
        private final boolean enabled;
        private final List<IInventorySlot> slots;
        private final List<Direction> seenSides = new ArrayList<>();

        private TestInventory(Direction defaultSide, boolean enabled, List<IInventorySlot> slots) {
            this.defaultSide = defaultSide;
            this.enabled = enabled;
            this.slots = slots;
            if (!enabled) {
                slots.clear();
            }
        }

        @Override
        public Direction getInventorySideFor() {
            return defaultSide;
        }

        @Override
        public boolean hasInventory() {
            return enabled;
        }

        @Override
        public List<IInventorySlot> getInventorySlots(@Nullable Direction side) {
            seenSides.add(side);
            return slots;
        }

        @Override
        public void onContentsChanged() {
        }
    }
}
