package mekanism.fabric.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import org.junit.jupiter.api.Test;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;

class FabricStrictEnergyHandlerTest {

    @Test
    void queriesIndexesSupportSetAndNonPositivePathsStayTrueLong() {
        SimpleEnergyStorage storage = new SimpleEnergyStorage(5_000_000_000L, Long.MAX_VALUE, Long.MAX_VALUE);
        storage.amount = 3_000_000_000L;
        FabricStrictEnergyHandler handler = new FabricStrictEnergyHandler(storage, conversion(true, 1));
        assertEquals(1, handler.getEnergyContainerCount());
        assertEquals(3_000_000_000L, handler.getEnergy(0));
        assertEquals(5_000_000_000L, handler.getMaxEnergy(0));
        assertEquals(2_000_000_000L, handler.getNeededEnergy(0));
        assertEquals(2_500_000_000L, handler.extractEnergy(2_500_000_000L, Action.SIMULATE));
        assertEquals(3_000_000_000L, storage.amount);
        assertEquals(0, handler.getEnergy(-1));
        assertEquals(0, handler.getMaxEnergy(1));
        assertEquals(0, handler.getNeededEnergy(2));
        handler.setEnergy(0, 7);
        assertEquals(3_000_000_000L, storage.amount);
        assertEquals(25, handler.insertEnergy(1, 25, Action.EXECUTE));
        assertEquals(0, handler.extractEnergy(-1, 25, Action.EXECUTE));
        assertEquals(0, handler.insertEnergy(0, Action.EXECUTE));
        assertEquals(-4, handler.insertEnergy(-4, Action.SIMULATE));
        assertEquals(0, handler.extractEnergy(0, Action.EXECUTE));
        assertEquals(0, handler.extractEnergy(-4, Action.SIMULATE));

        SimpleEnergyStorage unsupported = new SimpleEnergyStorage(100, 0, 0);
        unsupported.amount = 50;
        FabricStrictEnergyHandler noSupport = new FabricStrictEnergyHandler(unsupported, conversion(true, 1));
        assertEquals(20, noSupport.insertEnergy(20, Action.EXECUTE));
        assertEquals(0, noSupport.extractEnergy(20, Action.EXECUTE));
        assertEquals(50, unsupported.amount);

        FabricStrictEnergyHandler disabled = new FabricStrictEnergyHandler(storage, conversion(false, 1));
        assertEquals(0, disabled.getEnergy(0));
        assertEquals(0, disabled.getMaxEnergy(0));
        assertEquals(0, disabled.getNeededEnergy(0));
        assertEquals(20, disabled.insertEnergy(20, Action.EXECUTE));
        assertEquals(0, disabled.extractEnergy(20, Action.EXECUTE));
        assertEquals(3_000_000_000L, storage.amount);
    }

    @Test
    void simulateAbortsExecuteCommitsAndExistingOuterAbortRestoresTeamStorage() {
        SimpleEnergyStorage storage = new SimpleEnergyStorage(100, 100, 100);
        storage.amount = 10;
        FabricStrictEnergyHandler handler = new FabricStrictEnergyHandler(storage, conversion(true, 1));

        assertEquals(0, handler.insertEnergy(40, Action.SIMULATE));
        assertEquals(10, storage.amount);
        assertEquals(0, handler.insertEnergy(40, Action.EXECUTE));
        assertEquals(50, storage.amount);
        assertEquals(20, handler.extractEnergy(20, Action.SIMULATE));
        assertEquals(50, storage.amount);

        try (Transaction outer = Transaction.openOuter()) {
            assertEquals(20, handler.extractEnergy(20, Action.EXECUTE));
            assertEquals(30, storage.amount);
            // Deliberately do not commit: the handler's committed child must remain part of this outer transaction.
        }
        assertEquals(50, storage.amount);

        try (Transaction outer = Transaction.openOuter()) {
            assertEquals(0, handler.insertEnergy(10, Action.EXECUTE));
            assertEquals(60, storage.amount);
            outer.commit();
        }
        assertEquals(60, storage.amount);
    }

    @Test
    void fractionalPartialAcceptanceUsesFixedPairsAndExecutionMismatchRollsBack() {
        SimpleEnergyStorage twoPointFiveStorage = new SimpleEnergyStorage(3, 3, 3);
        FabricStrictEnergyHandler twoPointFive = new FabricStrictEnergyHandler(twoPointFiveStorage, conversion(true, 2.5));
        assertEquals(95, twoPointFive.insertEnergy(100, Action.EXECUTE));
        assertEquals(2, twoPointFiveStorage.amount);
        assertEquals(5, twoPointFive.extractEnergy(100, Action.EXECUTE));
        assertEquals(0, twoPointFiveStorage.amount);

        SimpleEnergyStorage pointFourStorage = new SimpleEnergyStorage(9, 9, 9);
        FabricStrictEnergyHandler pointFour = new FabricStrictEnergyHandler(pointFourStorage, conversion(true, 0.4));
        assertEquals(98, pointFour.insertEnergy(100, Action.EXECUTE));
        assertEquals(5, pointFourStorage.amount);
        assertEquals(2, pointFour.extractEnergy(100, Action.EXECUTE));
        assertEquals(0, pointFourStorage.amount);

        UnstableStorage unstableInsert = new UnstableStorage(100, 0);
        FabricStrictEnergyHandler insertion = new FabricStrictEnergyHandler(unstableInsert, conversion(true, 1));
        assertEquals(10, insertion.insertEnergy(10, Action.EXECUTE));
        assertEquals(0, unstableInsert.amount);
        assertEquals(2, unstableInsert.insertCalls);

        UnstableStorage unstableExtract = new UnstableStorage(100, 20);
        FabricStrictEnergyHandler extraction = new FabricStrictEnergyHandler(unstableExtract, conversion(true, 1));
        assertEquals(0, extraction.extractEnergy(10, Action.EXECUTE));
        assertEquals(20, unstableExtract.amount);
        assertEquals(2, unstableExtract.extractCalls);
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

    private static final class UnstableStorage extends SnapshotParticipant<Long> implements EnergyStorage {
        private final long capacity;
        private long amount;
        private int insertCalls;
        private int extractCalls;

        private UnstableStorage(long capacity, long amount) {
            this.capacity = capacity;
            this.amount = amount;
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            long accepted = Math.min(capacity - amount, insertCalls++ == 0 ? maxAmount : Math.max(0, maxAmount - 1));
            if (accepted > 0) {
                updateSnapshots(transaction);
                amount += accepted;
            }
            return accepted;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            long extracted = Math.min(amount, extractCalls++ == 0 ? maxAmount : Math.max(0, maxAmount - 1));
            if (extracted > 0) {
                updateSnapshots(transaction);
                amount -= extracted;
            }
            return extracted;
        }

        @Override
        public long getAmount() {
            return amount;
        }

        @Override
        public long getCapacity() {
            return capacity;
        }

        @Override
        protected Long createSnapshot() {
            return amount;
        }

        @Override
        protected void readSnapshot(Long snapshot) {
            amount = snapshot;
        }
    }
}
