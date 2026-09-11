package mekanism.fabric.energy;

import java.util.Objects;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IStrictEnergyHandler;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;

/**
 * Presents one Team Reborn {@link EnergyStorage} as a true-long Mekanism handler.
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
        if (amount <= 0 || !FabricEnergyConversions.isUsable(conversion) || !storage.supportsInsertion()) {
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
        if (amount <= 0 || !FabricEnergyConversions.isUsable(conversion) || !storage.supportsExtraction()) {
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
        try (Transaction transaction = openTransaction()) {
            return storage.insert(amount, transaction);
        }
    }

    private long simulateExtract(long amount) {
        try (Transaction transaction = openTransaction()) {
            return storage.extract(amount, transaction);
        }
    }

    private boolean executeInsert(long otherUnits, long joules) {
        try (Transaction transaction = openTransaction()) {
            long inserted = storage.insert(otherUnits, transaction);
            if (inserted == otherUnits && FabricEnergyConversions.isExactPair(conversion, otherUnits, joules)) {
                transaction.commit();
                return true;
            }
        }
        return false;
    }

    private boolean executeExtract(long otherUnits, long joules) {
        try (Transaction transaction = openTransaction()) {
            long extracted = storage.extract(otherUnits, transaction);
            if (extracted == otherUnits && FabricEnergyConversions.isExactPair(conversion, otherUnits, joules)) {
                transaction.commit();
                return true;
            }
        }
        return false;
    }

    /**
     * This bridge has no transaction parameter. The pinned Transfer API explicitly permits current-transaction
     * access for compatibility adapters; nesting preserves rollback by an existing outer transaction.
     */
    @SuppressWarnings("deprecation")
    private static Transaction openTransaction() {
        TransactionContext parent = Transaction.getCurrentUnsafe();
        return Transaction.openNested(parent);
    }
}
