package mekanism.fabric.energy;

import java.util.Objects;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IStrictEnergyHandler;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

/**
 * Transactional Team Reborn Energy view of one stable Mekanism handler.
 *
 * <p>The container count is captured at construction. Owners must retain one adapter for each stable handler and
 * physical side. Rollback necessarily restores through {@link IStrictEnergyHandler#setEnergy}; listeners, saves, or
 * synchronization triggered immediately by execute/restore cannot be made transactional by this compatibility layer.
 */
public final class FabricEnergyStorage extends SnapshotParticipant<long[]> implements EnergyStorage {

    private final IStrictEnergyHandler handler;
    private final IEnergyConversion conversion;
    private final int containerCount;

    FabricEnergyStorage(IStrictEnergyHandler handler, IEnergyConversion conversion) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.conversion = Objects.requireNonNull(conversion, "conversion");
        this.containerCount = handler.getEnergyContainerCount();
        if (containerCount < 0) {
            throw new IllegalArgumentException("Energy container count may not be negative: " + containerCount);
        }
    }

    @Override
    public boolean supportsInsertion() {
        checkStableContainerCount();
        return FabricEnergyConversions.isUsable(conversion);
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        checkStableContainerCount();
        StoragePreconditions.notNegative(maxAmount);
        Objects.requireNonNull(transaction, "transaction");
        if (maxAmount == 0 || !FabricEnergyConversions.isUsable(conversion)) {
            return 0;
        }
        long offeredJoules = FabricEnergyConversions.joulesForOther(conversion, maxAmount);
        if (offeredJoules == 0) {
            return 0;
        }
        long simulatedRemainder = handler.insertEnergy(offeredJoules, Action.SIMULATE);
        if (simulatedRemainder < 0 || simulatedRemainder >= offeredJoules) {
            return 0;
        }
        long simulatedAccepted = offeredJoules - simulatedRemainder;
        long acceptedOther = FabricEnergyConversions.otherForJoules(conversion, simulatedAccepted);
        long acceptedJoules = FabricEnergyConversions.fromOther(conversion, acceptedOther);
        if (!FabricEnergyConversions.isExactPair(conversion, acceptedOther, acceptedJoules)) {
            return 0;
        }
        try (Transaction nested = Transaction.openNested(transaction)) {
            checkStableContainerCount();
            updateSnapshots(nested);
            long remainder = handler.insertEnergy(acceptedJoules, Action.EXECUTE);
            checkStableContainerCount();
            if (remainder == 0 && FabricEnergyConversions.isExactPair(conversion, acceptedOther, acceptedJoules)) {
                nested.commit();
                return acceptedOther;
            }
        }
        return 0;
    }

    @Override
    public boolean supportsExtraction() {
        checkStableContainerCount();
        return FabricEnergyConversions.isUsable(conversion);
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        checkStableContainerCount();
        StoragePreconditions.notNegative(maxAmount);
        Objects.requireNonNull(transaction, "transaction");
        if (maxAmount == 0 || !FabricEnergyConversions.isUsable(conversion)) {
            return 0;
        }
        long requestedJoules = FabricEnergyConversions.joulesForOther(conversion, maxAmount);
        if (requestedJoules == 0) {
            return 0;
        }
        long simulatedExtracted = handler.extractEnergy(requestedJoules, Action.SIMULATE);
        if (simulatedExtracted <= 0 || simulatedExtracted > requestedJoules) {
            return 0;
        }
        long extractedOther = FabricEnergyConversions.otherForJoules(conversion, simulatedExtracted);
        long extractedJoules = FabricEnergyConversions.fromOther(conversion, extractedOther);
        if (!FabricEnergyConversions.isExactPair(conversion, extractedOther, extractedJoules)) {
            return 0;
        }
        try (Transaction nested = Transaction.openNested(transaction)) {
            checkStableContainerCount();
            updateSnapshots(nested);
            long extracted = handler.extractEnergy(extractedJoules, Action.EXECUTE);
            checkStableContainerCount();
            if (extracted == extractedJoules && FabricEnergyConversions.isExactPair(conversion, extractedOther, extractedJoules)) {
                nested.commit();
                return extractedOther;
            }
        }
        return 0;
    }

    @Override
    public long getAmount() {
        checkStableContainerCount();
        if (!FabricEnergyConversions.isUsable(conversion)) {
            return 0;
        }
        long amount = 0;
        for (int container = 0; container < containerCount; container++) {
            amount = addClamped(amount, FabricEnergyConversions.toOther(conversion, handler.getEnergy(container)));
        }
        return amount;
    }

    @Override
    public long getCapacity() {
        checkStableContainerCount();
        if (!FabricEnergyConversions.isUsable(conversion)) {
            return 0;
        }
        long capacity = 0;
        for (int container = 0; container < containerCount; container++) {
            capacity = addClamped(capacity, FabricEnergyConversions.toOther(conversion, handler.getMaxEnergy(container)));
        }
        return capacity;
    }

    @Override
    protected long[] createSnapshot() {
        checkStableContainerCount();
        long[] snapshot = new long[containerCount];
        for (int container = 0; container < containerCount; container++) {
            snapshot[container] = handler.getEnergy(container);
        }
        return snapshot;
    }

    @Override
    protected void readSnapshot(long[] snapshot) {
        checkStableContainerCount();
        if (snapshot.length != containerCount) {
            throw new IllegalStateException("Energy snapshot container count changed from " + containerCount + " to " + snapshot.length);
        }
        for (int container = 0; container < containerCount; container++) {
            handler.setEnergy(container, snapshot[container]);
        }
    }

    boolean hasConversion(IEnergyConversion candidate) {
        return conversion == candidate;
    }

    IStrictEnergyHandler stableHandler() {
        checkStableContainerCount();
        return handler;
    }

    private void checkStableContainerCount() {
        int current = handler.getEnergyContainerCount();
        if (current != containerCount) {
            throw new IllegalStateException(
                  "Energy container count changed from " + containerCount + " to " + current);
        }
    }

    private static long addClamped(long total, long value) {
        return value > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + value;
    }
}
