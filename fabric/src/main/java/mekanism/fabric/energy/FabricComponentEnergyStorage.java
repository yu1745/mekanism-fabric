package mekanism.fabric.energy;

import java.util.function.Function;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.common.attachments.containers.energy.ComponentBackedEnergyHandler;
import mekanism.fabric.item.ComponentItemContext;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import team.reborn.energy.api.EnergyStorage;

/** Team Energy item exposure using the actual component-backed handler and the owning inventory transaction. */
public final class FabricComponentEnergyStorage implements EnergyStorage {
    private final ComponentItemContext<ComponentBackedEnergyHandler> context;
    private final IEnergyConversion conversion;

    public FabricComponentEnergyStorage(ContainerItemContext context, Item item,
          Function<ItemStack, ComponentBackedEnergyHandler> provider, IEnergyConversion conversion) {
        this.context = new ComponentItemContext<>(context, item, provider);
        this.conversion = conversion;
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        return transfer(maxAmount, transaction, true);
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        return transfer(maxAmount, transaction, false);
    }

    private long transfer(long maxAmount, TransactionContext transaction, boolean insert) {
        StoragePreconditions.notNegative(maxAmount);
        if (maxAmount == 0 || !FabricEnergyConversions.isUsable(conversion)) return 0;
        var state = context.read();
        if (state == null) return 0;
        var handler = state.handler();
        long offered = FabricEnergyConversions.joulesForOther(conversion, maxAmount);
        long simulated = insert ? offered - handler.insertEnergy(offered, null, Action.SIMULATE)
              : handler.extractEnergy(offered, null, Action.SIMULATE);
        if (simulated < 0 || simulated > offered) return 0;
        long accepted = FabricEnergyConversions.otherForJoules(conversion, simulated);
        long joules = FabricEnergyConversions.fromOther(conversion, accepted);
        if (accepted == 0 || !FabricEnergyConversions.isExactPair(conversion, accepted, joules)) return 0;
        long executed = insert ? joules - handler.insertEnergy(joules, null, Action.EXECUTE)
              : handler.extractEnergy(joules, null, Action.EXECUTE);
        if (executed != joules) return 0;
        // Component handlers may intentionally implement creative sources/sinks. Their returned transfer is authoritative;
        // no live item was mutated, so rollback is the inventory exchange's responsibility, not per-container setters.
        if (!FabricEnergyConversions.isExactPair(conversion, accepted, joules)) return 0;
        try (Transaction nested = transaction.openNested()) {
            if (!context.exchange(state, nested)) return 0;
            nested.commit();
            return accepted;
        }
    }

    @Override
    public long getAmount() {
        return total(false);
    }

    @Override
    public long getCapacity() {
        return total(true);
    }

    private long total(boolean capacity) {
        if (!FabricEnergyConversions.isUsable(conversion)) return 0;
        var state = context.read();
        if (state == null) return 0;
        long total = 0;
        for (int i = 0; i < state.handler().size(); i++) {
            long amount = Math.max(0, capacity ? state.handler().getMaxEnergy(i, null) : state.handler().getEnergy(i, null));
            total = amount > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + amount;
        }
        return FabricEnergyConversions.otherForJoules(conversion, total);
    }

    @Override
    public boolean supportsInsertion() {
        return FabricEnergyConversions.isUsable(conversion) && context.read() != null;
    }

    @Override
    public boolean supportsExtraction() {
        return supportsInsertion();
    }
}
