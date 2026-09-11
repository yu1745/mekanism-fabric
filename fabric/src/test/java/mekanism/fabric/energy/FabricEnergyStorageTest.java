package mekanism.fabric.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IStrictEnergyHandler;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import team.reborn.energy.api.base.SimpleEnergyStorage;

class FabricEnergyStorageTest {

    @Test
    void preconditionsSupportSaturationAndOrdinaryFractionalReturnsMatchRawState() {
        ArrayHandler saturatedHandler = new ArrayHandler(
              new long[]{Long.MAX_VALUE - 5, 10}, new long[]{Long.MAX_VALUE, 20});
        FabricEnergyStorage saturated = new FabricEnergyStorage(saturatedHandler, Direction.NORTH, conversion(true, 1));
        assertTrue(saturated.supportsInsertion());
        assertTrue(saturated.supportsExtraction());
        assertEquals(Long.MAX_VALUE, saturated.getAmount());
        assertEquals(Long.MAX_VALUE, saturated.getCapacity());
        try (Transaction transaction = Transaction.openOuter()) {
            assertThrows(IllegalArgumentException.class, () -> saturated.insert(-1, transaction));
            assertThrows(IllegalArgumentException.class, () -> saturated.extract(-1, transaction));
        }

        long oddAboveDoublePrecision = (1L << 53) + 1;
        ArrayHandler preciseHandler = new ArrayHandler(
              new long[]{oddAboveDoublePrecision, 3}, new long[]{oddAboveDoublePrecision + 100, 7});
        FabricEnergyStorage precise = new FabricEnergyStorage(preciseHandler, Direction.NORTH, conversion(true, 1));
        assertEquals(oddAboveDoublePrecision + 3, precise.getAmount());
        assertEquals(oddAboveDoublePrecision + 107, precise.getCapacity());
        ArrayHandler aggregateBeforeConversion = new ArrayHandler(new long[]{2, 3}, new long[]{2, 3});
        FabricEnergyStorage aggregate = new FabricEnergyStorage(
              aggregateBeforeConversion, Direction.NORTH, conversion(true, 2.5));
        assertEquals(2, aggregate.getAmount());
        assertEquals(2, aggregate.getCapacity());

        FabricEnergyStorage disabled = new FabricEnergyStorage(
              saturatedHandler, Direction.NORTH, conversion(false, 1));
        assertFalse(disabled.supportsInsertion());
        assertFalse(disabled.supportsExtraction());
        assertEquals(0, disabled.getAmount());
        assertEquals(0, disabled.getCapacity());
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, disabled.insert(10, transaction));
            assertEquals(0, disabled.extract(10, transaction));
            assertThrows(IllegalArgumentException.class, () -> disabled.insert(-1, transaction));
        }
        FabricEnergyStorage invalid = new FabricEnergyStorage(
              saturatedHandler, Direction.NORTH, conversion(true, Double.NaN));
        assertFalse(invalid.supportsInsertion());
        assertFalse(invalid.supportsExtraction());

        ArrayHandler ordinaryHandler = new ArrayHandler(new long[]{10}, new long[]{100});
        FabricEnergyStorage ordinary = new FabricEnergyStorage(ordinaryHandler, Direction.NORTH, conversion(true, 1));
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(15, ordinary.insert(15, transaction));
            assertEquals(25, ordinaryHandler.energy[0]);
            assertEquals(5, ordinary.extract(5, transaction));
            assertEquals(20, ordinaryHandler.energy[0]);
            transaction.commit();
        }
        assertEquals(20, ordinaryHandler.energy[0]);

        ArrayHandler hugeHandler = new ArrayHandler(
              new long[]{oddAboveDoublePrecision}, new long[]{Long.MAX_VALUE});
        FabricEnergyStorage huge = new FabricEnergyStorage(hugeHandler, Direction.NORTH, conversion(true, 1));
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = (1L << 59) + 1;
            assertEquals(inserted, huge.insert(inserted, transaction));
            assertEquals(oddAboveDoublePrecision + inserted, hugeHandler.energy[0]);
            long extracted = (1L << 58) + 1;
            assertEquals(extracted, huge.extract(extracted, transaction));
            assertEquals(oddAboveDoublePrecision + inserted - extracted, hugeHandler.energy[0]);
            transaction.commit();
        }

        ArrayHandler fractionalHandler = new ArrayHandler(new long[]{0}, new long[]{100});
        FabricEnergyStorage fractional = new FabricEnergyStorage(
              fractionalHandler, Direction.NORTH, conversion(true, 2.5));
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(2, fractional.insert(3, transaction));
            assertEquals(5, fractionalHandler.energy[0]);
            transaction.commit();
        }
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(2, fractional.extract(3, transaction));
            assertEquals(0, fractionalHandler.energy[0]);
            transaction.commit();
        }
        assertEquals(0, fractionalHandler.energy[0]);

        ArrayHandler inverseFractionalHandler = new ArrayHandler(new long[]{0}, new long[]{100});
        FabricEnergyStorage inverseFractional = new FabricEnergyStorage(
              inverseFractionalHandler, Direction.NORTH, conversion(true, 0.4));
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(5, inverseFractional.insert(9, transaction));
            assertEquals(2, inverseFractionalHandler.energy[0]);
            transaction.commit();
        }
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(5, inverseFractional.extract(9, transaction));
            assertEquals(0, inverseFractionalHandler.energy[0]);
            transaction.commit();
        }
        assertEquals(0, inverseFractionalHandler.energy[0]);

        SimpleEnergyStorage teamStorage = new SimpleEnergyStorage(100, 100, 100);
        FabricStrictEnergyHandler nonRestoring = new FabricStrictEnergyHandler(teamStorage, conversion(true, 1));
        assertThrows(IllegalArgumentException.class,
              () -> new FabricEnergyStorage(nonRestoring, Direction.NORTH, conversion(true, 1)));
    }

    @Test
    void stableSnapshotsCoverRepeatedNestedAbortOuterAbortCommitAndCountFailure() {
        ArrayHandler handler = new ArrayHandler(new long[]{10, 20}, new long[]{100, 100});
        FabricEnergyStorage storage = new FabricEnergyStorage(handler, Direction.NORTH, conversion(true, 1));

        try (Transaction outer = Transaction.openOuter()) {
            assertEquals(5, storage.insert(5, outer));
            assertEquals(5, storage.insert(5, outer));
            assertEquals(List.of(20L, 20L), handler.values());
            assertEquals(2, handler.listenerCalls);

            try (Transaction nestedAbort = outer.openNested()) {
                assertEquals(7, storage.extract(7, nestedAbort));
                assertEquals(List.of(13L, 20L), handler.values());
                assertEquals(3, handler.listenerCalls);
            }
            assertEquals(List.of(20L, 20L), handler.values());
            assertEquals(4, handler.listenerCalls);

            try (Transaction nestedCommit = outer.openNested()) {
                assertEquals(4, storage.extract(4, nestedCommit));
                assertEquals(List.of(16L, 20L), handler.values());
                assertEquals(5, handler.listenerCalls);
                nestedCommit.commit();
            }
            assertEquals(List.of(16L, 20L), handler.values());
            // Deliberately abort the outer transaction after the nested commit.
        }
        assertEquals(List.of(10L, 20L), handler.values());
        assertEquals(6, handler.listenerCalls);

        try (Transaction outerCommit = Transaction.openOuter()) {
            assertEquals(6, storage.insert(6, outerCommit));
            assertEquals(List.of(16L, 20L), handler.values());
            assertEquals(7, handler.listenerCalls);
            outerCommit.commit();
        }
        assertEquals(List.of(16L, 20L), handler.values());
        assertEquals(7, handler.listenerCalls);
        assertEquals(List.of("0=15", "0=20", "0=13", "0=20", "1=20", "0=16", "0=10", "1=20", "0=16"),
              handler.setEvents);

        ArrayHandler changing = new ArrayHandler(new long[]{1}, new long[]{10});
        FabricEnergyStorage unstableCount = new FabricEnergyStorage(changing, Direction.NORTH, conversion(true, 1));
        changing.reportedCount = 2;
        assertThrows(IllegalStateException.class, unstableCount::supportsInsertion);
        assertThrows(IllegalStateException.class, unstableCount::getAmount);
        try (Transaction transaction = Transaction.openOuter()) {
            assertThrows(IllegalStateException.class, () -> unstableCount.insert(1, transaction));
        }

        ArrayHandler changesDuringExecute = new ArrayHandler(new long[]{4}, new long[]{10});
        FabricEnergyStorage guarded = new FabricEnergyStorage(
              changesDuringExecute, Direction.NORTH, conversion(true, 1));
        changesDuringExecute.changeCountOnExecute = true;
        try (Transaction transaction = Transaction.openOuter()) {
            assertEquals(0, guarded.insert(3, transaction));
            assertEquals(List.of(4L), changesDuringExecute.values());
            transaction.commit();
        }
        assertEquals(List.of(4L), changesDuringExecute.values());
        assertThrows(IllegalStateException.class, guarded::supportsInsertion);

        // setEnergy is an explicit backing precondition, not a guarantee the adapter can synthesize. All four
        // captured indices are attempted even after index 1 fails; index 2 is actually restored before index 3
        // supplies a suppressed failure. Index 1's mutation survives, proving atomicity is unavailable and close
        // propagates when the precondition is violated.
        ArrayHandler throwing = new ArrayHandler(
              new long[]{10, 20, 30, 40}, new long[]{10, 100, 100, 100});
        FabricEnergyStorage cannotRestore = new FabricEnergyStorage(
              throwing, Direction.NORTH, conversion(true, 1));
        Transaction failedRestore = Transaction.openOuter();
        assertEquals(85, cannotRestore.insert(85, failedRestore));
        assertEquals(List.of(10L, 100L, 35L, 40L), throwing.values());
        throwing.throwOnSet[1] = true;
        throwing.throwOnSet[3] = true;
        RuntimeException closeFailure = assertThrows(RuntimeException.class, failedRestore::close);
        assertEquals(List.of(10L, 100L, 30L, 40L), throwing.values());
        assertEquals(List.of("1=100", "2=35", "0=10", "1=20", "2=30", "3=40"), throwing.setEvents);
        Throwable aggregateFailure = closeFailure.getCause();
        assertNotNull(aggregateFailure);
        Throwable firstSetterFailure = aggregateFailure.getCause();
        assertNotNull(firstSetterFailure);
        assertEquals(1, firstSetterFailure.getSuppressed().length);
        assertTrue(firstSetterFailure.getMessage().contains("container 1"));
        assertTrue(firstSetterFailure.getSuppressed()[0].getMessage().contains("container 3"));
    }

    private static IEnergyConversion conversion(boolean enabled, double rate) {
        return new IEnergyConversion() {
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public double getConversion() {
                return rate;
            }
        };
    }

    private static final class ArrayHandler implements IStrictEnergyHandler {
        private final long[] energy;
        private final long[] capacity;
        private final boolean[] throwOnSet;
        private final List<String> setEvents = new ArrayList<>();
        private int reportedCount;
        private int listenerCalls;
        private boolean changeCountOnExecute;

        private ArrayHandler(long[] energy, long[] capacity) {
            this.energy = energy;
            this.capacity = capacity;
            this.throwOnSet = new boolean[energy.length];
            this.reportedCount = energy.length;
        }

        private List<Long> values() {
            List<Long> values = new ArrayList<>(energy.length);
            for (long value : energy) {
                values.add(value);
            }
            return values;
        }

        @Override
        public int getEnergyContainerCount() {
            return reportedCount;
        }

        @Override
        public long getEnergy(int container) {
            return energy[container];
        }

        @Override
        public void setEnergy(int container, long value) {
            setEvents.add(container + "=" + value);
            if (throwOnSet[container]) {
                throw new UnsupportedOperationException("cannot restore container " + container);
            }
            long previous = energy[container];
            energy[container] = value;
            if (previous != value) {
                // Mirrors the immediate onContentsChanged behavior of Mekanism containers.
                listenerCalls++;
            }
        }

        @Override
        public long getMaxEnergy(int container) {
            return capacity[container];
        }

        @Override
        public long getNeededEnergy(int container) {
            return capacity[container] - energy[container];
        }

        @Override
        public long insertEnergy(int container, long amount, Action action) {
            if (amount <= 0) {
                return amount;
            }
            long accepted = Math.min(amount, capacity[container] - energy[container]);
            if (accepted > 0 && action.execute()) {
                setEnergy(container, energy[container] + accepted);
                if (changeCountOnExecute) {
                    changeCountOnExecute = false;
                    reportedCount = energy.length + 1;
                }
            }
            return amount - accepted;
        }

        @Override
        public long extractEnergy(int container, long amount, Action action) {
            if (amount <= 0) {
                return 0;
            }
            long extracted = Math.min(amount, energy[container]);
            if (extracted > 0 && action.execute()) {
                setEnergy(container, energy[container] - extracted);
                if (changeCountOnExecute) {
                    changeCountOnExecute = false;
                    reportedCount = energy.length + 1;
                }
            }
            return extracted;
        }
    }
}
