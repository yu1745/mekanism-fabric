package mekanism.fabric.energy;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IStrictEnergyHandler;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import team.reborn.energy.api.EnergyStorage;

/**
 * Transactional Team Reborn Energy view of one stable, physical-side-bound Mekanism handler.
 *
 * <p>The container count and exposure side are captured at construction. Every captured container is required to
 * support deterministic restoration through {@link IStrictEnergyHandler#setEnergy}; construction rejects the known
 * non-restoring {@link FabricStrictEnergyHandler}, but arbitrary handlers must uphold this caller precondition.
 * Restore attempts every captured index before propagating a failure. If any setter cannot restore, atomicity is no
 * longer possible and transaction close reports the failure. Listeners, saves, or synchronization triggered
 * immediately by execute/restore cannot be made transactional by this compatibility layer.
 */
public final class FabricEnergyStorage extends SnapshotParticipant<long[]> implements EnergyStorage {

    private final IStrictEnergyHandler handler;
    private final Direction exposureSide;
    private final IEnergyConversion conversion;
    private final BooleanSupplier insertionAllowed;
    private final BooleanSupplier extractionAllowed;
    private final int containerCount;

    FabricEnergyStorage(IStrictEnergyHandler handler, Direction exposureSide, IEnergyConversion conversion) {
        this(handler, exposureSide, conversion, () -> true, () -> true);
    }

    FabricEnergyStorage(IStrictEnergyHandler handler, Direction exposureSide, IEnergyConversion conversion,
          BooleanSupplier insertionAllowed, BooleanSupplier extractionAllowed) {
        this.handler = Objects.requireNonNull(handler, "handler");
        if (handler instanceof FabricStrictEnergyHandler) {
            throw new IllegalArgumentException("FabricStrictEnergyHandler cannot provide transactional setEnergy restoration");
        }
        this.exposureSide = Objects.requireNonNull(exposureSide, "exposureSide");
        this.conversion = Objects.requireNonNull(conversion, "conversion");
        this.insertionAllowed = Objects.requireNonNull(insertionAllowed, "insertionAllowed");
        this.extractionAllowed = Objects.requireNonNull(extractionAllowed, "extractionAllowed");
        this.containerCount = handler.getEnergyContainerCount();
        if (containerCount < 0) {
            throw new IllegalArgumentException("Energy container count may not be negative: " + containerCount);
        }
    }

    @Override
    public boolean supportsInsertion() {
        checkStableContainerCount();
        return FabricEnergyConversions.isUsable(conversion) && insertionAllowed.getAsBoolean();
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        checkStableContainerCount();
        StoragePreconditions.notNegative(maxAmount);
        Objects.requireNonNull(transaction, "transaction");
        if (maxAmount == 0 || !FabricEnergyConversions.isUsable(conversion) || !insertionAllowed.getAsBoolean()) {
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
            // This is the final stable-topology and live-policy check before mutation.
            checkStableContainerCount();
            if (!insertionAllowed.getAsBoolean()) {
                return 0;
            }
            updateSnapshots(nested);
            long remainder = handler.insertEnergy(acceptedJoules, Action.EXECUTE);
            // A count change caused by execute invalidates the result, but must not prevent this child from aborting
            // and restoring the captured topology. The next public operation will fail its entry check.
            if (hasStableContainerCount() && remainder == 0
                  && FabricEnergyConversions.isExactPair(conversion, acceptedOther, acceptedJoules)) {
                nested.commit();
                return acceptedOther;
            }
        }
        return 0;
    }

    @Override
    public boolean supportsExtraction() {
        checkStableContainerCount();
        return FabricEnergyConversions.isUsable(conversion) && extractionAllowed.getAsBoolean();
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        checkStableContainerCount();
        StoragePreconditions.notNegative(maxAmount);
        Objects.requireNonNull(transaction, "transaction");
        if (maxAmount == 0 || !FabricEnergyConversions.isUsable(conversion) || !extractionAllowed.getAsBoolean()) {
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
            if (!extractionAllowed.getAsBoolean()) {
                return 0;
            }
            updateSnapshots(nested);
            long extracted = handler.extractEnergy(extractedJoules, Action.EXECUTE);
            if (hasStableContainerCount() && extracted == extractedJoules
                  && FabricEnergyConversions.isExactPair(conversion, extractedOther, extractedJoules)) {
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
        long joules = 0;
        for (int container = 0; container < containerCount; container++) {
            joules = addClamped(joules, handler.getEnergy(container));
        }
        return FabricEnergyConversions.toOther(conversion, joules);
    }

    @Override
    public long getCapacity() {
        checkStableContainerCount();
        if (!FabricEnergyConversions.isUsable(conversion)) {
            return 0;
        }
        long joules = 0;
        for (int container = 0; container < containerCount; container++) {
            joules = addClamped(joules, handler.getMaxEnergy(container));
        }
        return FabricEnergyConversions.toOther(conversion, joules);
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
        // Never consult the handler's now-reported count here: execution may have changed it. The snapshot length
        // is the captured topology, and every captured index gets a best-effort restoration attempt.
        Throwable failure = null;
        for (int container = 0; container < snapshot.length; container++) {
            try {
                handler.setEnergy(container, snapshot[container]);
            } catch (Throwable restoreFailure) {
                if (failure == null) {
                    failure = restoreFailure;
                } else if (failure != restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
        }
        if (failure != null) {
            // Always propagate as an Exception so Transfer API can finish all callbacks and repair its lifecycle.
            throw new IllegalStateException("Failed to restore one or more captured energy containers", failure);
        }
    }

    boolean hasConversion(IEnergyConversion candidate) {
        return conversion == candidate;
    }

    boolean isExposedOn(Direction side) {
        return exposureSide == side;
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

    private boolean hasStableContainerCount() {
        return handler.getEnergyContainerCount() == containerCount;
    }

    private static long addClamped(long total, long value) {
        if (value <= 0) {
            return total;
        }
        return value > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + value;
    }
}
