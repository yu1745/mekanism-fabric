package mekanism.common.capabilities.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.SerializationConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class BasicEnergyContainerTest {
    private static final HolderLookup.Provider EMPTY_PROVIDER = HolderLookup.Provider.create(Stream.empty());
    @Test
    void simulationReportsTransferWithoutMutatingOrNotifying() {
        AtomicInteger notifications = new AtomicInteger();
        BasicEnergyContainer container = BasicEnergyContainer.create(100, notifications::incrementAndGet);

        assertEquals(0, container.insert(60, Action.SIMULATE, AutomationType.INTERNAL));
        assertEquals(0, container.getEnergy());
        assertEquals(0, notifications.get());

        assertEquals(0, container.insert(60, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(60, container.getEnergy());
        assertEquals(1, notifications.get());

        assertEquals(40, container.insert(80, Action.SIMULATE, AutomationType.INTERNAL));
        assertEquals(60, container.getEnergy());
        assertEquals(1, notifications.get());

        assertEquals(40, container.insert(80, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(100, container.getEnergy());
        assertEquals(2, notifications.get());

        assertEquals(30, container.extract(30, Action.SIMULATE, AutomationType.EXTERNAL));
        assertEquals(100, container.getEnergy());
        assertEquals(2, notifications.get());

        assertEquals(100, container.extract(150, Action.EXECUTE, AutomationType.EXTERNAL));
        assertEquals(0, container.getEnergy());
        assertEquals(3, notifications.get());
    }

    @Test
    void factoryPoliciesPreserveEveryAutomationRule() {
        assertPolicy(() -> BasicEnergyContainer.input(100, null), automation -> true,
              automation -> automation != AutomationType.EXTERNAL);
        assertPolicy(() -> BasicEnergyContainer.output(100, null),
              automation -> automation == AutomationType.INTERNAL, automation -> true);
        assertPolicy(() -> BasicEnergyContainer.create(100, null), automation -> true, automation -> true);
        assertPolicy(() -> BasicEnergyContainer.create(100,
                    automation -> automation == AutomationType.MANUAL,
                    automation -> automation == AutomationType.EXTERNAL, null),
              automation -> automation == AutomationType.EXTERNAL,
              automation -> automation == AutomationType.MANUAL);
    }

    @Test
    void rateHooksApplyEquallyToSimulationAndExecution() {
        AtomicInteger notifications = new AtomicInteger();
        RateLimitedEnergyContainer container = new RateLimitedEnergyContainer(100, 15, 7, notifications::incrementAndGet);

        assertEquals(35, container.insert(50, Action.SIMULATE, AutomationType.INTERNAL));
        assertEquals(0, container.getEnergy());
        assertEquals(0, notifications.get());
        assertEquals(35, container.insert(50, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(15, container.getEnergy());
        assertEquals(1, notifications.get());

        assertEquals(7, container.extract(50, Action.SIMULATE, AutomationType.INTERNAL));
        assertEquals(15, container.getEnergy());
        assertEquals(1, notifications.get());
        assertEquals(7, container.extract(50, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(8, container.getEnergy());
        assertEquals(2, notifications.get());

        RateLimitedEnergyContainer stopped = new RateLimitedEnergyContainer(100, 0, 0, notifications::incrementAndGet);
        stopped.setEnergy(50);
        notifications.set(0);
        assertEquals(20, stopped.insert(20, Action.SIMULATE, AutomationType.INTERNAL));
        assertEquals(0, stopped.extract(20, Action.SIMULATE, AutomationType.INTERNAL));
        assertEquals(20, stopped.insert(20, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(0, stopped.extract(20, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(50, stopped.getEnergy());
        assertEquals(0, notifications.get());
    }

    @Test
    void directSetClampsAndNotifiesOnlyForRealChanges() {
        AtomicInteger notifications = new AtomicInteger();
        BasicEnergyContainer container = BasicEnergyContainer.create(100, notifications::incrementAndGet);

        container.setEnergy(150);
        assertEquals(100, container.getEnergy());
        assertEquals(1, notifications.get());

        container.setEnergy(200);
        assertEquals(100, container.getEnergy());
        assertEquals(1, notifications.get());

        assertThrows(IllegalArgumentException.class, () -> container.setEnergy(-1));
        assertEquals(100, container.getEnergy());
        assertEquals(1, notifications.get());
    }

    @Test
    void nonPositiveTransfersRemainNoOps() {
        AtomicInteger notifications = new AtomicInteger();
        BasicEnergyContainer container = BasicEnergyContainer.create(100, notifications::incrementAndGet);
        container.setEnergy(50);
        notifications.set(0);

        assertEquals(0, container.insert(0, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(-5, container.insert(-5, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(0, container.extract(0, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(0, container.extract(-5, Action.EXECUTE, AutomationType.INTERNAL));
        assertEquals(50, container.getEnergy());
        assertEquals(0, notifications.get());
    }

    @Test
    void nbtRoundTripAndLegacyStringsPreserveOfficialSemantics() {
        BasicEnergyContainer source = BasicEnergyContainer.create(100, null);
        assertFalse(source.serializeNBT(EMPTY_PROVIDER).contains(SerializationConstants.STORED));
        source.setEnergy(75);
        CompoundTag serialized = source.serializeNBT(EMPTY_PROVIDER);
        assertEquals(75, serialized.getLong(SerializationConstants.STORED));

        BasicEnergyContainer restored = BasicEnergyContainer.create(100, null);
        restored.deserializeNBT(EMPTY_PROVIDER, serialized);
        assertEquals(75, restored.getEnergy());

        CompoundTag decimalLegacy = new CompoundTag();
        decimalLegacy.putString(SerializationConstants.STORED, "42.875");
        restored.deserializeNBT(EMPTY_PROVIDER, decimalLegacy);
        assertEquals(42, restored.getEnergy());

        CompoundTag unsignedOverflow = new CompoundTag();
        unsignedOverflow.putString(SerializationConstants.STORED, "18446744073709551615");
        restored.deserializeNBT(EMPTY_PROVIDER, unsignedOverflow);
        assertEquals(100, restored.getEnergy());

        CompoundTag invalid = new CompoundTag();
        invalid.putString(SerializationConstants.STORED, "not-energy");
        restored.deserializeNBT(EMPTY_PROVIDER, invalid);
        assertTrue(restored.isEmpty());

        restored.setEnergy(25);
        restored.deserializeNBT(EMPTY_PROVIDER, new CompoundTag());
        assertEquals(25, restored.getEnergy());
    }

    private static void assertPolicy(Supplier<BasicEnergyContainer> factory, Predicate<AutomationType> canInsert,
          Predicate<AutomationType> canExtract) {
        for (AutomationType automation : AutomationType.values()) {
            BasicEnergyContainer insertion = factory.get();
            assertEquals(canInsert.test(automation) ? 0 : 40,
                  insertion.insert(40, Action.EXECUTE, automation), "insert " + automation);
            assertEquals(canInsert.test(automation) ? 40 : 0, insertion.getEnergy(), "stored after insert " + automation);

            BasicEnergyContainer extraction = factory.get();
            extraction.setEnergy(40);
            assertEquals(canExtract.test(automation) ? 10 : 0,
                  extraction.extract(10, Action.EXECUTE, automation), "extract " + automation);
            assertEquals(canExtract.test(automation) ? 30 : 40,
                  extraction.getEnergy(), "stored after extract " + automation);
        }
    }

    private static final class RateLimitedEnergyContainer extends BasicEnergyContainer {
        private final long insertRate;
        private final long extractRate;

        private RateLimitedEnergyContainer(long capacity, long insertRate, long extractRate,
              IContentsListener listener) {
            super(capacity, automation -> true, automation -> true, listener);
            this.insertRate = insertRate;
            this.extractRate = extractRate;
        }

        @Override
        protected long getInsertRate(AutomationType automationType) {
            return insertRate;
        }

        @Override
        protected long getExtractRate(AutomationType automationType) {
            return extractRate;
        }
    }
}

