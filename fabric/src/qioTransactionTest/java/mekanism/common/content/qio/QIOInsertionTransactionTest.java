package mekanism.common.content.qio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mekanism.api.Action;
import mekanism.api.security.SecurityMode;
import mekanism.common.inventory.slot.QIODriveSlot;
import mekanism.common.item.ItemQIODrive;
import mekanism.common.lib.frequency.TileComponentFrequency;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tier.QIODriveTier;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QIOInsertionTransactionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        MekanismDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
    }

    @BeforeEach
    void resetLookup() {
        QIOGlobalItemLookup.INSTANCE.reset();
    }

    @Test
    void outerAbortRollsBackCommittedNestedInsertionAndGlobalUuid() {
        Holder holder = new Holder();
        ItemStack item = new ItemStack(Items.STONE, 12);
        HashedItem type = HashedItem.create(item);
        try (Transaction outer = Transaction.openOuter()) {
            try (Transaction nested = outer.openNested()) {
                assertEquals(12, holder.frequency.insertItem(item, 12, nested));
                nested.commit();
            }
            assertEquals(12, holder.frequency.getStored(item));
            assertNull(QIOGlobalItemLookup.INSTANCE.getUUIDForType(type));
            assertEquals(0, holder.updates);
        }
        assertEquals(0, holder.frequency.getStored(item));
        assertNull(QIOGlobalItemLookup.INSTANCE.getUUIDForType(type));
        assertEquals(0, holder.updates);
        assertEquals(0, holder.slots.getFirst().getStack().get(MekanismDataComponents.DRIVE_METADATA.get()).count());
    }

    @Test
    void committedInsertionAssignsUuidAndNotifiesRealDriveHolder() {
        Holder holder = new Holder();
        ItemStack item = new ItemStack(Items.DIRT, 7);
        try (Transaction tx = Transaction.openOuter()) {
            assertEquals(7, holder.frequency.insertItem(item, 7, tx));
            assertEquals(0, holder.updates);
            tx.commit();
        }
        assertEquals(7, holder.frequency.getStored(item));
        assertNotNull(QIOGlobalItemLookup.INSTANCE.getUUIDForType(HashedItem.create(item)));
        assertEquals(1, holder.updates);
        assertEquals(7, holder.slots.getFirst().getStack().get(MekanismDataComponents.DRIVE_METADATA.get()).count());
    }

    @Test
    void shorterRealExtractionCommitsOnlyWhatFrequencyActuallyReceives() {
        Holder holder = new Holder();
        ItemStack item = new ItemStack(Items.IRON_INGOT, 8);
        SimpleContainer inventory = new SimpleContainer(item.copy());
        var view = InventoryStorage.of(inventory, null).getSlot(0);
        ItemVariant variant = ItemVariant.of(item);
        try (Transaction simulated = Transaction.openOuter()) {
            assertEquals(8, view.extract(variant, 8, simulated));
        }
        inventory.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        try (Transaction tx = Transaction.openOuter()) {
            long actual = view.extract(variant, 8, tx);
            assertEquals(3, actual);
            assertEquals(actual, holder.frequency.insertItem(item, actual, tx));
            tx.commit();
        }
        assertEquals(0, inventory.getItem(0).getCount());
        assertEquals(3, holder.frequency.getStored(item));
        assertEquals(1, holder.updates);
    }

    @Test
    void exceptionRollsBackBothRealSourceAndFrequency() {
        Holder holder = new Holder();
        ItemStack item = new ItemStack(Items.GOLD_INGOT, 6);
        SimpleContainer inventory = new SimpleContainer(item.copy());
        var view = InventoryStorage.of(inventory, null).getSlot(0);
        ItemVariant variant = ItemVariant.of(item);
        assertThrows(IllegalStateException.class, () -> {
            try (Transaction tx = Transaction.openOuter()) {
                long actual = view.extract(variant, 6, tx);
                assertEquals(actual, holder.frequency.insertItem(item, actual, tx));
                throw new IllegalStateException("abort transfer");
            }
        });
        assertEquals(6, inventory.getItem(0).getCount());
        assertEquals(0, holder.frequency.getStored(item));
        assertNull(QIOGlobalItemLookup.INSTANCE.getUUIDForType(HashedItem.create(item)));
        assertEquals(0, holder.updates);
    }

    @Test
    void abortRestoresEveryDriveAcrossCapacityBoundary() {
        Holder holder = new Holder();
        holder.addDrive();
        ItemStack item = new ItemStack(Items.COPPER_INGOT);
        try (Transaction tx = Transaction.openOuter()) {
            assertEquals(16_001, holder.frequency.insertItem(item, 16_001, tx));
            assertEquals(16_001, holder.frequency.getStored(item));
        }
        assertEquals(0, holder.frequency.getStored(item));
        for (QIODriveSlot slot : holder.slots) {
            assertEquals(0, slot.getStack().get(MekanismDataComponents.DRIVE_METADATA.get()).count());
        }
        assertEquals(0, holder.updates);
    }

    @Test
    void nestedAbortPreservesExistingTypeAndUuid() {
        Holder holder = new Holder();
        ItemStack item = new ItemStack(Items.DIAMOND, 4);
        try (Transaction tx = Transaction.openOuter()) {
            assertEquals(4, holder.frequency.insertItem(item, 4, tx));
            tx.commit();
        }
        UUID existing = QIOGlobalItemLookup.INSTANCE.getUUIDForType(HashedItem.create(item));
        try (Transaction outer = Transaction.openOuter()) {
            try (Transaction nested = outer.openNested()) {
                assertEquals(4, holder.frequency.insertItem(item, 4, nested));
                nested.commit();
            }
        }
        assertEquals(4, holder.frequency.getStored(item));
        assertEquals(existing, QIOGlobalItemLookup.INSTANCE.getUUIDForType(HashedItem.create(item)));
        assertEquals(4, holder.slots.getFirst().getStack().get(MekanismDataComponents.DRIVE_METADATA.get()).count());
        assertEquals(1, holder.updates);
    }

    @Test
    void replacementDriveIsNeverOverwrittenDuringRollback() {
        Holder holder = new Holder();
        ItemStack item = new ItemStack(Items.EMERALD, 2);
        ItemStack replacement = new ItemStack(new ItemQIODrive(QIODriveTier.BASE, new Item.Properties()));
        ItemStack[] actualReplacement = new ItemStack[1];
        assertThrows(RuntimeException.class, () -> {
            try (Transaction tx = Transaction.openOuter()) {
                assertEquals(2, holder.frequency.insertItem(item, 2, tx));
                holder.slots.getFirst().setStack(replacement);
                actualReplacement[0] = holder.slots.getFirst().getStack();
            }
        });
        assertSame(actualReplacement[0], holder.slots.getFirst().getStack());
        assertEquals(0, actualReplacement[0].get(MekanismDataComponents.DRIVE_METADATA.get()).count());
    }

    @Test
    void nonTransactionalReentryCannotBeLostByOuterRollback() {
        Holder holder = new Holder();
        ItemStack first = new ItemStack(Items.STONE, 3);
        ItemStack second = new ItemStack(Items.DIRT, 4);
        try (Transaction tx = Transaction.openOuter()) {
            assertEquals(3, holder.frequency.insertItem(first, 3, tx));
            assertThrows(IllegalStateException.class, () -> holder.frequency.addItem(second));
            QIODriveData drive = holder.frequency.getAllDrives().iterator().next();
            assertThrows(IllegalStateException.class, () -> drive.add(HashedItem.create(second), 4, Action.EXECUTE));
            assertThrows(IllegalStateException.class, () -> holder.frequency.removeDrive(holder.slots.getFirst().getKey(), true));
        }
        assertEquals(0, holder.frequency.getStored(first));
        assertEquals(0, holder.frequency.getStored(second));
        //The abort also releases the guard, so normal legacy insertion is still available.
        assertEquals(0, holder.frequency.addItem(second).getCount());
        assertEquals(4, holder.frequency.getStored(second));
    }

    @Test
    void serverDriveSlotRejectsReentrantInsertionBeforeMutating() {
        Holder holder = new Holder();
        Level serverLevel = mekanism.test.TileLevelStub.create();
        int[] notifications = {0};
        QIODriveSlot empty = new QIODriveSlot(holder, holder.slots.size(), () -> serverLevel, () -> notifications[0]++, 0, 0);
        holder.slots.add(empty);
        ItemStack drive = new ItemStack(new ItemQIODrive(QIODriveTier.BASE, new Item.Properties()));
        try (Transaction tx = Transaction.openOuter()) {
            assertEquals(3, holder.frequency.insertItem(new ItemStack(Items.STONE, 3), 3, tx));
            assertTrue(empty.insertItem(drive, Action.SIMULATE, mekanism.api.AutomationType.MANUAL).isEmpty());
            assertThrows(IllegalStateException.class, () -> empty.setStack(drive));
            assertThrows(IllegalStateException.class, () -> empty.setStackUnchecked(drive));
            assertThrows(IllegalStateException.class, () -> empty.insertItem(drive, Action.EXECUTE, mekanism.api.AutomationType.MANUAL));
            assertTrue(empty.isEmpty());
            assertEquals(0, notifications[0]);
        }
        empty.setStack(drive);
        assertEquals(1, notifications[0]);
        assertNotNull(holder.frequency.getDriveData(empty.getKey()));
    }

    @Test
    void finalCommitNotificationFailureDoesNotUndoOnlyOneSide() {
        Holder holder = new Holder();
        ItemStack item = new ItemStack(Items.IRON_INGOT, 5);
        SimpleContainer inventory = new SimpleContainer(item.copy());
        var view = InventoryStorage.of(inventory, null).getSlot(0);
        ItemVariant variant = ItemVariant.of(item);
        holder.failNotification = true;
        assertThrows(RuntimeException.class, () -> {
            try (Transaction tx = Transaction.openOuter()) {
                long extracted = view.extract(variant, 5, tx);
                assertEquals(5, extracted);
                assertEquals(extracted, holder.frequency.insertItem(item, extracted, tx));
                tx.commit();
            }
        });
        assertEquals(0, inventory.getItem(0).getCount());
        assertEquals(5, holder.frequency.getStored(item));
        assertNotNull(QIOGlobalItemLookup.INSTANCE.getUUIDForType(HashedItem.create(item)));
        holder.failNotification = false;
        assertEquals(0, holder.frequency.addItem(new ItemStack(Items.DIRT)).getCount());
    }

    private static final class Holder implements IQIODriveHolder {
        private final QIOFrequency frequency = new QIOFrequency("test", UUID.randomUUID(), SecurityMode.PUBLIC);
        private final List<QIODriveSlot> slots = new ArrayList<>();
        private int updates;
        private boolean failNotification;

        private Holder() {
            addDrive();
        }

        private void addDrive() {
            QIODriveSlot slot = new QIODriveSlot(this, slots.size(), () -> null, null, 0, 0);
            slots.add(slot);
            slot.setStack(new ItemStack(new ItemQIODrive(QIODriveTier.BASE, new Item.Properties())));
            frequency.addDrive(slot.getKey());
        }

        @Override public List<QIODriveSlot> getDriveSlots() { return slots; }
        @Override public void onDataUpdate() {
            updates++;
            if (failNotification) {
                throw new IllegalStateException("holder notification failed");
            }
        }
        @Override public QIOFrequency getQIOFrequency() { return frequency; }
        @Override public TileComponentFrequency getFrequencyComponent() { throw new UnsupportedOperationException(); }
        @Override public @Nullable Level getLevel() { return null; }
    }
}
