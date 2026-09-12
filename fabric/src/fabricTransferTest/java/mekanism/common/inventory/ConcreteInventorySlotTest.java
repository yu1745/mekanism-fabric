package mekanism.common.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.inventory.container.IGUIWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.inventory.container.slot.ArmorSlot;
import mekanism.common.inventory.container.slot.HotBarSlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.slot.MainInventorySlot;
import mekanism.common.inventory.container.slot.OffhandSlot;
import mekanism.common.inventory.container.slot.VirtualInventoryContainerSlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.warning.ISupportsWarning;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConcreteInventorySlotTest {
    private static HolderLookup.Provider provider;

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        provider = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void basicSlotPreservesValidationLimitsSimulationExecutionAndNotifications() {
        AtomicInteger notifications = new AtomicInteger();
        BasicInventorySlot slot = BasicInventorySlot.at(stack -> stack.is(Items.IRON_INGOT), notifications::incrementAndGet, 7, 9, 10);
        assertEquals(7, slot.getGuiX());
        ItemStack offered = new ItemStack(Items.IRON_INGOT, 12);
        assertEquals(2, slot.insertItem(offered, Action.SIMULATE, AutomationType.EXTERNAL).getCount());
        assertTrue(slot.isEmpty());
        assertEquals(0, notifications.get());
        assertEquals(2, slot.insertItem(offered, Action.EXECUTE, AutomationType.EXTERNAL).getCount());
        assertEquals(10, slot.getCount());
        assertEquals(1, notifications.get());
        assertEquals(4, slot.extractItem(4, Action.SIMULATE, AutomationType.INTERNAL).getCount());
        assertEquals(10, slot.getCount());
        assertEquals(4, slot.extractItem(4, Action.EXECUTE, AutomationType.INTERNAL).getCount());
        assertEquals(6, slot.getCount());
        assertEquals(2, notifications.get());
        assertEquals(3, slot.insertItem(new ItemStack(Items.GOLD_INGOT, 3), Action.EXECUTE, AutomationType.MANUAL).getCount());
    }

    @Test
    void inputAndOutputSlotsPreserveAutomationPoliciesAndContainerTypes() {
        InputInventorySlot input = InputInventorySlot.at(stack -> stack.is(Items.COAL), stack -> !stack.is(Items.DIRT), null, 0, 0);
        input.setStack(new ItemStack(Items.COAL, 2));
        assertTrue(input.extractItem(1, Action.SIMULATE, AutomationType.EXTERNAL).isEmpty());
        assertEquals(1, input.extractItem(1, Action.SIMULATE, AutomationType.INTERNAL).getCount());
        assertEquals(1, input.extractItem(1, Action.SIMULATE, AutomationType.MANUAL).getCount());
        assertEquals(1, input.insertItem(new ItemStack(Items.DIAMOND), Action.SIMULATE, AutomationType.INTERNAL).getCount());
        assertTrue(input.insertItem(new ItemStack(Items.COAL), Action.SIMULATE, AutomationType.EXTERNAL).isEmpty());

        OutputInventorySlot output = OutputInventorySlot.at(null, 0, 0);
        assertEquals(1, output.insertItem(new ItemStack(Items.REDSTONE), Action.SIMULATE, AutomationType.EXTERNAL).getCount());
        assertTrue(output.insertItem(new ItemStack(Items.REDSTONE), Action.SIMULATE, AutomationType.INTERNAL).isEmpty());
        output.setStack(new ItemStack(Items.REDSTONE, 2));
        assertEquals(1, output.extractItem(1, Action.SIMULATE, AutomationType.EXTERNAL).getCount());
        assertEquals(mekanism.common.inventory.container.slot.ContainerSlotType.INPUT, input.createContainerSlot().getSlotType());
        assertEquals(mekanism.common.inventory.container.slot.ContainerSlotType.OUTPUT, output.createContainerSlot().getSlotType());
    }

    @Test
    void nbtRoundTripAndUncheckedRestorePreserveOversizedOrNowInvalidStacks() {
        BasicInventorySlot source = BasicInventorySlot.at(null, 0, 0);
        source.setStack(new ItemStack(Items.DIAMOND, 37));
        CompoundTag serialized = source.serializeNBT(provider);
        BasicInventorySlot target = BasicInventorySlot.at(stack -> false, null, 0, 0);
        target.deserializeNBT(provider, serialized);
        assertTrue(target.getStack().is(Items.DIAMOND));
        assertEquals(37, target.getCount());
        target.deserializeNBT(provider, new CompoundTag());
        assertTrue(target.isEmpty());
    }

    @Test
    void realInventoryContainerSlotDelegatesManualInsertRemovalAndUncheckedSet() {
        AtomicInteger notifications = new AtomicInteger();
        BasicInventorySlot backing = BasicInventorySlot.at(stack -> stack.is(Items.EMERALD), notifications::incrementAndGet, 4, 5);
        InventoryContainerSlot container = backing.createContainerSlot();
        assertTrue(container.mayPlace(new ItemStack(Items.EMERALD)));
        assertFalse(container.mayPlace(new ItemStack(Items.DIRT)));
        assertTrue(container.insertItem(new ItemStack(Items.EMERALD, 5), Action.SIMULATE).isEmpty());
        assertTrue(container.insertItem(new ItemStack(Items.EMERALD, 5), Action.EXECUTE).isEmpty());
        assertEquals(5, backing.getCount());
        assertEquals(1, container.remove(1).getCount());
        container.set(new ItemStack(Items.DIRT, 6));
        assertTrue(backing.getStack().is(Items.DIRT));
        assertTrue(container.getMaxStackSize(new ItemStack(Items.DIRT, 6)) >= 6);
        assertEquals(6, backing.getCount());
        assertTrue(notifications.get() >= 3);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void playerSlotsIconsInsertionAndInventorySlotInfoUseVanillaContracts() {
        SimpleContainer inventory = new SimpleContainer(41);
        MainInventorySlot main = new MainInventorySlot(inventory, 9, 0, 0);
        HotBarSlot hotbar = new HotBarSlot(inventory, 0, 0, 0);
        assertTrue(main.insertItem(new ItemStack(Items.APPLE, 3), Action.SIMULATE).isEmpty());
        assertTrue(main.insertItem(new ItemStack(Items.APPLE, 3), Action.EXECUTE).isEmpty());
        assertEquals(3, inventory.getItem(9).getCount());
        assertTrue(hotbar.insertItem(new ItemStack(Items.STICK), Action.EXECUTE).isEmpty());

        ArmorSlot helmet = new ArmorSlot(new Inventory(null), 39, 0, 0, EquipmentSlot.HEAD);
        OffhandSlot offhand = new OffhandSlot(inventory, 40, 0, 0, null);
        assertEquals(Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET), helmet.getNoItemIcon());
        assertEquals(Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD), offhand.getNoItemIcon());

        BasicInventorySlot empty = BasicInventorySlot.at(null, 0, 0);
        InventorySlotInfo info = new InventorySlotInfo(true, false, List.of(empty));
        assertTrue(info.canInput());
        assertFalse(info.canOutput());
        assertTrue(info.hasSlot(empty));
        assertTrue(info.isEmpty());
        empty.setStack(new ItemStack(Items.QUARTZ));
        assertFalse(info.isEmpty());
    }

    @Test
    void virtualSlotWindowCoordinatesRenderStateAndWarningsStayConnected() {
        BasicInventorySlot backing = BasicInventorySlot.at(null, 0, 0);
        SelectedWindowData windowData = new SelectedWindowData(WindowType.CRAFTING, (byte) 1);
        VirtualInventoryContainerSlot virtual = new VirtualInventoryContainerSlot(backing, windowData, null, backing::setStackUnchecked);
        IGUIWindow window = new IGUIWindow() { };
        virtual.updatePosition(window, () -> 17, () -> 23);
        ItemStack render = new ItemStack(Items.COPPER_INGOT, 2);
        virtual.updateRenderInfo(render, true, "override");
        assertSame(window, virtual.getLinkedWindow());
        assertEquals(17, virtual.getActualX());
        assertEquals(23, virtual.getActualY());
        assertSame(render, virtual.getStackToRender());
        assertTrue(virtual.shouldDrawOverlay());
        assertEquals("override", virtual.getTooltipOverride());
        assertSame(virtual, virtual.getSlot());
        assertTrue(virtual.exists(new SelectedWindowData(WindowType.CRAFTING, (byte) 1)));
        assertFalse(virtual.exists(new SelectedWindowData(WindowType.CRAFTING, (byte) 2)));

        AtomicReference<ISupportsWarning<?>> added = new AtomicReference<>();
        backing.tracksWarnings(added::set);
        InventoryContainerSlot warningSlot = backing.createContainerSlot();
        WarningSupport warning = new WarningSupport();
        warningSlot.addWarnings(warning);
        assertSame(warning, added.get());
    }

    private static final class WarningSupport implements ISupportsWarning<WarningSupport> {
        @Override
        public WarningSupport warning(WarningType type, java.util.function.BooleanSupplier supplier) {
            return this;
        }
    }
}
