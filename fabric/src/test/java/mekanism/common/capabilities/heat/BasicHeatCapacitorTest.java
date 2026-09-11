package mekanism.common.capabilities.heat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import mekanism.api.SerializationConstants;
import mekanism.api.heat.HeatAPI;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class BasicHeatCapacitorTest {
    private static final HolderLookup.Provider EMPTY_PROVIDER = HolderLookup.Provider.create(Stream.empty());

    @Test
    void factoryValidatesOnlyOfficialBounds() {
        assertThrows(IllegalArgumentException.class, () -> BasicHeatCapacitor.create(0.99, null, null));
        assertThrows(IllegalArgumentException.class, () -> BasicHeatCapacitor.create(1, 0.99, 0, null, null));

        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(1, 1, -5, null, null);
        assertEquals(1, capacitor.getHeatCapacity());
        assertEquals(1, capacitor.getInverseConduction());
        assertEquals(-5, capacitor.getInverseInsulation());

        BasicHeatCapacitor nonFinite = BasicHeatCapacitor.create(Double.NaN, Double.NaN, Double.POSITIVE_INFINITY, null, null);
        assertTrue(Double.isNaN(nonFinite.getHeatCapacity()));
        assertTrue(Double.isNaN(nonFinite.getInverseConduction()));
        assertEquals(Double.POSITIVE_INFINITY, nonFinite.getInverseInsulation());
    }

    @Test
    void storedHeatInitializesLazilyFromAmbientAndCapacity() {
        AtomicInteger ambientReads = new AtomicInteger();
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(4, () -> {
            ambientReads.incrementAndGet();
            return 275;
        }, null);

        assertEquals(0, ambientReads.get());
        assertEquals(1_100, capacitor.getHeat());
        assertEquals(275, capacitor.getTemperature());
        assertEquals(1, ambientReads.get());
        assertTrue(capacitor.isAmbientTemperature());

        BasicHeatCapacitor defaultAmbient = BasicHeatCapacitor.create(2, null, null);
        assertEquals(600, defaultAmbient.getHeat());
        assertEquals(HeatAPI.AMBIENT_TEMP, defaultAmbient.getTemperature());
    }

    @Test
    void handleHeatBatchesTransfersAndNotifiesOnceOnUpdate() {
        AtomicInteger notifications = new AtomicInteger();
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(2, () -> 300, notifications::incrementAndGet);
        assertEquals(600, capacitor.getHeat());

        capacitor.handleHeat(40);
        capacitor.handleHeat(-10);
        assertEquals(600, capacitor.getHeat());
        assertEquals(0, notifications.get());
        capacitor.update();
        assertEquals(630, capacitor.getHeat());
        assertEquals(1, notifications.get());

        capacitor.handleHeat(25);
        capacitor.handleHeat(-25);
        capacitor.update();
        assertEquals(630, capacitor.getHeat());
        assertEquals(1, notifications.get());
    }

    @Test
    void epsilonTransferRemainsQueuedUntilAggregateExceedsThreshold() {
        AtomicInteger notifications = new AtomicInteger();
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(1, null, notifications::incrementAndGet);
        double initial = capacitor.getHeat();
        double subEpsilon = HeatAPI.EPSILON / 2;

        capacitor.handleHeat(subEpsilon);
        capacitor.update();
        assertEquals(initial, capacitor.getHeat());
        assertEquals(0, notifications.get());

        capacitor.handleHeat(HeatAPI.EPSILON);
        capacitor.update();
        assertEquals(initial + subEpsilon + HeatAPI.EPSILON, capacitor.getHeat());
        assertEquals(1, notifications.get());
    }

    @Test
    void directSetAndCapacityChangesPreserveOfficialNotificationRules() {
        AtomicInteger notifications = new AtomicInteger();
        BasicHeatCapacitor capacitor = BasicHeatCapacitor.create(2, () -> 300, notifications::incrementAndGet);
        assertEquals(600, capacitor.getHeat());

        capacitor.setHeat(600);
        assertEquals(0, notifications.get());
        capacitor.setHeat(-20);
        assertEquals(-20, capacitor.getHeat());
        assertEquals(1, notifications.get());

        capacitor.setHeat(600);
        notifications.set(0);
        capacitor.setHeatCapacity(4, true);
        assertEquals(1_200, capacitor.getHeat());
        assertEquals(300, capacitor.getTemperature());
        assertEquals(1, notifications.get());

        capacitor.setHeatCapacity(5, false);
        assertEquals(1_200, capacitor.getHeat());
        assertEquals(240, capacitor.getTemperature());
        assertEquals(1, notifications.get());

        capacitor.setHeatCapacityFromPacket(6);
        assertEquals(1_200, capacitor.getHeat());
        assertEquals(200, capacitor.getTemperature());
        assertEquals(1, notifications.get());
    }

    @Test
    void nbtUsesStrictDoublesAndDoesNotNotifyOrClearPendingHeat() {
        BasicHeatCapacitor source = BasicHeatCapacitor.create(2, () -> 300, null);
        source.setHeat(725.5);
        CompoundTag serialized = source.serializeNBT(EMPTY_PROVIDER);
        assertEquals(725.5, serialized.getDouble(SerializationConstants.STORED));
        assertEquals(2, serialized.getDouble(SerializationConstants.HEAT_CAPACITY));

        AtomicInteger notifications = new AtomicInteger();
        BasicHeatCapacitor restored = BasicHeatCapacitor.create(1, null, notifications::incrementAndGet);
        restored.handleHeat(10);
        restored.deserializeNBT(EMPTY_PROVIDER, serialized);
        assertEquals(725.5, restored.getHeat());
        assertEquals(2, restored.getHeatCapacity());
        assertEquals(0, notifications.get());
        restored.update();
        assertEquals(735.5, restored.getHeat());
        assertEquals(1, notifications.get());

        CompoundTag wrongTypes = new CompoundTag();
        wrongTypes.putLong(SerializationConstants.STORED, 50);
        wrongTypes.putInt(SerializationConstants.HEAT_CAPACITY, 9);
        restored.deserializeNBT(EMPTY_PROVIDER, wrongTypes);
        assertEquals(735.5, restored.getHeat());
        assertEquals(2, restored.getHeatCapacity());

        CompoundTag missing = new CompoundTag();
        restored.deserializeNBT(EMPTY_PROVIDER, missing);
        assertEquals(735.5, restored.getHeat());
        assertEquals(2, restored.getHeatCapacity());
        assertFalse(missing.contains(SerializationConstants.STORED));

        CompoundTag sentinel = new CompoundTag();
        sentinel.putDouble(SerializationConstants.STORED, -1);
        sentinel.putDouble(SerializationConstants.HEAT_CAPACITY, 4);
        BasicHeatCapacitor uninitialized = BasicHeatCapacitor.create(1, () -> 275, notifications::incrementAndGet);
        uninitialized.deserializeNBT(EMPTY_PROVIDER, sentinel);
        assertEquals(1_100, uninitialized.getHeat());
        assertEquals(4, uninitialized.getHeatCapacity());
    }
}
