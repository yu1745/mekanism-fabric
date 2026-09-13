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
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

/**
 * Transactional Team Reborn Energy view of one stable, physical-side-bound Mekanism handler.
 *
 * <p>The container count and exposure side are captured at construction. Every captured container is required to
 * support deterministic restoration through {@link IStrictEnergyHandler#setEnergy}; construction rejects the known
 * non-restoring {@link FabricStrictEnergyHandler}, but arbitrary handlers must uphold this caller precondition.
 * Restore attempts every captured index and reads it back before propagating a setter, read, or mismatch failure.
 * If any index cannot be restored exactly, atomicity is no longer possible and transaction close reports the
 * failure. Listeners, saves, or synchronization triggered immediately by execute/restore cannot be made
 * transactional by this compatibility layer.
 *
 * <p>Permanent lifetime revocation: {@link #invalidate()} makes this view fail closed forever. Retained references
 * can no longer transfer or read their old owner (queries and transfers report defaults and zero), and the reverse
 * stableHandler unwrap route stops returning the internal handler. A Joules-domain handler retained from the unwrap
 * route before invalidation fails closed the same way, while the raw backing handler stays live so transaction
 * rollback captured before invalidation still restores through this view and is never blocked by the guard.
 */
public final class FabricEnergyStorage extends SnapshotParticipant<long[]> implements EnergyStorage {

    private final IStrictEnergyHandler handler;
    private final Direction exposureSide;
    private final IEnergyConversion conversion;
    private final BooleanSupplier insertionAllowed;
    private final BooleanSupplier extractionAllowed;
    private final int containerCount;
    private final StableJoulesHandler stableHandler;
    private volatile boolean invalidated;

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
        //Created eagerly so the reverse unwrap route is one stable object for this storage's whole lifetime
        this.stableHandler = new StableJoulesHandler(this.handler);
    }

    /**
     * Permanently invalidates this side view. Retained references stay safe, stable objects but no longer expose
     * contents or permit transfer, and no longer unwrap to their old owner's internal handler. Snapshots captured
     * before invalidation still restore on transaction rollback.
     */
    public void invalidate() {
        invalidated = true;
        //Close the retained reverse-unwrap facade as well; the raw backing handler stays untouched for readSnapshot rollback
        stableHandler.revoke();
    }

    boolean isInvalidated() {
        return invalidated;
    }

    @Override
    public boolean supportsInsertion() {
        if (invalidated || !hasStableContainerCount() || !FabricEnergyConversions.isUsable(conversion)) {
            return false;
        }
        boolean allowed = insertionAllowed.getAsBoolean();
        return allowed && hasStableContainerCount();
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);
        Objects.requireNonNull(transaction, "transaction");
        if (invalidated) {
            return 0;
        }
        checkStableContainerCount();
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
            // and restoring the captured topology. Later mutations fail fast; non-mutating queries return defaults.
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
        if (invalidated || !hasStableContainerCount() || !FabricEnergyConversions.isUsable(conversion)) {
            return false;
        }
        boolean allowed = extractionAllowed.getAsBoolean();
        return allowed && hasStableContainerCount();
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notNegative(maxAmount);
        Objects.requireNonNull(transaction, "transaction");
        if (invalidated) {
            return 0;
        }
        checkStableContainerCount();
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
        if (invalidated || !hasStableContainerCount() || !FabricEnergyConversions.isUsable(conversion)) {
            return 0;
        }
        long joules = 0;
        for (int container = 0; container < containerCount; container++) {
            if (!hasStableContainerCount()) {
                return 0;
            }
            long energy;
            try {
                energy = handler.getEnergy(container);
            } catch (RuntimeException readFailure) {
                if (!hasStableContainerCount()) {
                    return 0;
                }
                throw readFailure;
            }
            if (!hasStableContainerCount()) {
                return 0;
            }
            joules = addClamped(joules, energy);
        }
        long amount = FabricEnergyConversions.toOther(conversion, joules);
        return hasStableContainerCount() ? amount : 0;
    }

    @Override
    public long getCapacity() {
        if (invalidated || !hasStableContainerCount() || !FabricEnergyConversions.isUsable(conversion)) {
            return 0;
        }
        long joules = 0;
        for (int container = 0; container < containerCount; container++) {
            if (!hasStableContainerCount()) {
                return 0;
            }
            long capacity;
            try {
                capacity = handler.getMaxEnergy(container);
            } catch (RuntimeException readFailure) {
                if (!hasStableContainerCount()) {
                    return 0;
                }
                throw readFailure;
            }
            if (!hasStableContainerCount()) {
                return 0;
            }
            joules = addClamped(joules, capacity);
        }
        long capacity = FabricEnergyConversions.toOther(conversion, joules);
        return hasStableContainerCount() ? capacity : 0;
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
        // is the captured topology, and every captured index gets a best-effort set attempt and read-back check.
        Throwable failure = null;
        for (int container = 0; container < snapshot.length; container++) {
            try {
                handler.setEnergy(container, snapshot[container]);
            } catch (Throwable restoreFailure) {
                failure = collectRestoreFailure(failure, restoreFailure);
            }
            try {
                long restored = handler.getEnergy(container);
                if (restored != snapshot[container]) {
                    failure = collectRestoreFailure(failure, new IllegalStateException(
                          "Energy container " + container + " restored " + restored + " instead of "
                                + snapshot[container]));
                }
            } catch (Throwable readFailure) {
                failure = collectRestoreFailure(failure, readFailure);
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

    @Nullable
    IStrictEnergyHandler stableHandler() {
        if (invalidated) {
            //A retained view must never hand out its old owner's live internal handler after invalidation
            return null;
        }
        checkStableContainerCount();
        return stableHandler;
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

    private static Throwable collectRestoreFailure(Throwable failure, Throwable restoreFailure) {
        if (failure == null) {
            return restoreFailure;
        }
        if (failure != restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
        return failure;
    }

    private static long addClamped(long total, long value) {
        if (value <= 0) {
            return total;
        }
        return value > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + value;
    }

    /**
     * Stable Joules-domain facade handed out through the reverse {@code stableHandler()} unwrap route. Delegation is exact Joules with no conversion roundtrip:
     * the facade forwards every domain read, setter, and transfer straight to the same raw side proxy this storage itself consults. Revocation (tied to this
     * storage's lifetime via {@link #invalidate()}) fails the facade closed permanently: reads report empty-handler defaults and setters/transfers are denied
     * without consulting the old owner. The raw backing handler itself is never revoked here, so transaction snapshots captured before invalidation still
     * restore through {@code readSnapshot}.
     */
    private static final class StableJoulesHandler implements IStrictEnergyHandler {

        private final IStrictEnergyHandler delegate;
        private volatile boolean revoked;

        private StableJoulesHandler(IStrictEnergyHandler delegate) {
            this.delegate = delegate;
        }

        private void revoke() {
            revoked = true;
        }

        @Override
        public int getEnergyContainerCount() {
            return isRevoked() ? 0 : delegate.getEnergyContainerCount();
        }

        @Override
        public long getEnergy(int container) {
            return isRevoked() ? 0 : delegate.getEnergy(container);
        }

        @Override
        public void setEnergy(int container, long energy) {
            if (!isRevoked()) {
                delegate.setEnergy(container, energy);
            }
        }

        @Override
        public long getMaxEnergy(int container) {
            return isRevoked() ? 0 : delegate.getMaxEnergy(container);
        }

        @Override
        public long getNeededEnergy(int container) {
            return isRevoked() ? 0 : delegate.getNeededEnergy(container);
        }

        @Override
        public long insertEnergy(int container, long amount, Action action) {
            return isRevoked() ? amount : delegate.insertEnergy(container, amount, action);
        }

        @Override
        public long extractEnergy(int container, long amount, Action action) {
            return isRevoked() ? 0 : delegate.extractEnergy(container, amount, action);
        }

        @Override
        public long insertEnergy(long amount, Action action) {
            return isRevoked() ? amount : delegate.insertEnergy(amount, action);
        }

        @Override
        public long extractEnergy(long amount, Action action) {
            return isRevoked() ? 0 : delegate.extractEnergy(amount, action);
        }

        private boolean isRevoked() {
            return revoked;
        }
    }
}
