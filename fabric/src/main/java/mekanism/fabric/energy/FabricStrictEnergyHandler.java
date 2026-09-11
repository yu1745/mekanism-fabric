package mekanism.fabric.energy;

import java.util.Objects;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IStrictEnergyHandler;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

/**
 * Presents one Team Reborn {@link EnergyStorage} as a true-long Mekanism handler.
 *
 * <p>Like Mekanism's official external-energy wrappers, arbitrary {@link #setEnergy} remains unsupported. This
 * handler must therefore never be used as the rollback backing of {@link FabricEnergyStorage}; that constructor
 * rejects it explicitly.
 */
public final class FabricStrictEnergyHandler implements IStrictEnergyHandler {

    private final EnergyStorage storage;
    private final IEnergyConversion conversion;

    FabricStrictEnergyHandler(EnergyStorage storage, IEnergyConversion conversion) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.conversion = Objects.requireNonNull(conversion, "conversion");
    }

    @Override
    public int getEnergyContainerCount() {
        return 1;
    }

    @Override
    public long getEnergy(int container) {
        return container == 0 ? FabricEnergyConversions.fromOther(conversion, storage.getAmount()) : 0;
    }

    @Override
    public void setEnergy(int container, long energy) {
        // Arbitrary assignment is not part of the external storage contract.
    }

    @Override
    public long getMaxEnergy(int container) {
        return container == 0 ? FabricEnergyConversions.fromOther(conversion, storage.getCapacity()) : 0;
    }

    @Override
    public long getNeededEnergy(int container) {
        if (container != 0 || !FabricEnergyConversions.isUsable(conversion)) {
            return 0;
        }
        long capacity = storage.getCapacity();
        long amount = storage.getAmount();
        return capacity > amount ? FabricEnergyConversions.fromOther(conversion, capacity - amount) : 0;
    }

    @Override
    public long insertEnergy(int container, long amount, Action action) {
        return container == 0 ? insertEnergy(amount, action) : amount;
    }

    @Override
    public long insertEnergy(long amount, Action action) {
        if (amount <= 0 || !FabricEnergyConversions.isUsable(conversion) || isTransactionClosing()
              || !storage.supportsInsertion()) {
            return amount;
        }
        long maxOther = FabricEnergyConversions.otherForJoules(conversion, amount);
        if (maxOther == 0) {
            return amount;
        }
        long simulated = simulateInsert(maxOther);
        if (simulated <= 0 || simulated > maxOther) {
            return amount;
        }
        long joules = FabricEnergyConversions.joulesForOther(conversion, simulated);
        long other = FabricEnergyConversions.toOther(conversion, joules);
        if (!FabricEnergyConversions.isExactPair(conversion, other, joules)) {
            return amount;
        }
        if (action.simulate()) {
            return amount - joules;
        }
        return executeInsert(other, joules) ? amount - joules : amount;
    }

    @Override
    public long extractEnergy(int container, long amount, Action action) {
        return container == 0 ? extractEnergy(amount, action) : 0;
    }

    @Override
    public long extractEnergy(long amount, Action action) {
        if (amount <= 0 || !FabricEnergyConversions.isUsable(conversion) || isTransactionClosing()
              || !storage.supportsExtraction()) {
            return 0;
        }
        long maxOther = FabricEnergyConversions.otherForJoules(conversion, amount);
        if (maxOther == 0) {
            return 0;
        }
        long simulated = simulateExtract(maxOther);
        if (simulated <= 0 || simulated > maxOther) {
            return 0;
        }
        long joules = FabricEnergyConversions.joulesForOther(conversion, simulated);
        long other = FabricEnergyConversions.toOther(conversion, joules);
        if (!FabricEnergyConversions.isExactPair(conversion, other, joules)) {
            return 0;
        }
        if (action.simulate()) {
            return joules;
        }
        return executeExtract(other, joules) ? joules : 0;
    }

    private long simulateInsert(long amount) {
        Transaction transaction = openTransaction();
        if (transaction == null) {
            return 0;
        }
        try (transaction) {
            return storage.insert(amount, transaction);
        }
    }

    private long simulateExtract(long amount) {
        Transaction transaction = openTransaction();
        if (transaction == null) {
            return 0;
        }
        try (transaction) {
            return storage.extract(amount, transaction);
        }
    }

    private boolean executeInsert(long otherUnits, long joules) {
        Transaction transaction = openTransaction();
        if (transaction == null) {
            return false;
        }
        try (transaction) {
            long inserted = storage.insert(otherUnits, transaction);
            if (inserted == otherUnits && FabricEnergyConversions.isExactPair(conversion, otherUnits, joules)) {
                transaction.commit();
                return true;
            }
        }
        return false;
    }

    private boolean executeExtract(long otherUnits, long joules) {
        Transaction transaction = openTransaction();
        if (transaction == null) {
            return false;
        }
        try (transaction) {
            long extracted = storage.extract(otherUnits, transaction);
            if (extracted == otherUnits && FabricEnergyConversions.isExactPair(conversion, otherUnits, joules)) {
                transaction.commit();
                return true;
            }
        }
        return false;
    }

    private static boolean isTransactionClosing() {
        Transaction.Lifecycle lifecycle = Transaction.getLifecycle();
        return lifecycle == Transaction.Lifecycle.CLOSING || lifecycle == Transaction.Lifecycle.OUTER_CLOSING;
    }

    /**
     * This bridge has no transaction parameter. The pinned Transfer API explicitly permits current-transaction
     * access for compatibility adapters while lifecycle is OPEN; nesting preserves rollback by that parent. During
     * close callbacks, obtaining the current transaction is forbidden, so callers short-circuit before this method.
     */
    @SuppressWarnings("deprecation")
    @Nullable
    private static Transaction openTransaction() {
        Transaction.Lifecycle lifecycle = Transaction.getLifecycle();
        if (lifecycle == Transaction.Lifecycle.NONE) {
            return Transaction.openOuter();
        }
        if (lifecycle == Transaction.Lifecycle.OPEN) {
            TransactionContext parent = Transaction.getCurrentUnsafe();
            return parent == null ? null : Transaction.openNested(parent);
        }
        return null;
    }
}
