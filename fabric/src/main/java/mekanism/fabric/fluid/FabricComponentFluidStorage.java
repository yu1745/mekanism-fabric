package mekanism.fabric.fluid;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.FluidStack;
import mekanism.common.attachments.containers.fluid.ComponentBackedFluidHandler;
import mekanism.fabric.item.ComponentItemContext;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Multi-tank item storage: whole millibuckets and component identity, with changes exchanged through the real item context. */
public final class FabricComponentFluidStorage implements SlottedStorage<FluidVariant> {
    private final ComponentItemContext<ComponentBackedFluidHandler> context;

    public FabricComponentFluidStorage(ContainerItemContext context, Item item, Function<ItemStack, ComponentBackedFluidHandler> provider) {
        this.context = new ComponentItemContext<>(context, item, provider);
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        return transfer(resource, maxAmount, transaction, true, -1);
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        return transfer(resource, maxAmount, transaction, false, -1);
    }

    private long transfer(FluidVariant resource, long maxAmount, TransactionContext transaction, boolean insert, int slot) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long units = Math.min(maxAmount / FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET, Integer.MAX_VALUE)
              * FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET;
        if (units == 0) return 0;
        var state = context.read();
        if (state == null || slot >= state.handler().size()) return 0;
        FluidStack offered = FabricFluidConversions.toFluidStack(resource, units);
        FluidStack result;
        if (slot < 0) {
            result = insert ? state.handler().insertFluid(offered, null, Action.EXECUTE)
                  : state.handler().extractFluid(offered, null, Action.EXECUTE);
        } else {
            var tank = state.handler().getFluidTank(slot, null);
            if (!insert && !FabricFluidConversions.toFabricVariant(tank.getFluid()).equals(resource)) return 0;
            result = insert ? tank.insert(offered, Action.EXECUTE, AutomationType.INTERNAL)
                  : tank.extract(offered.getAmount(), Action.EXECUTE, AutomationType.INTERNAL);
        }
        if (!result.isEmpty() && !resource.equals(FabricFluidConversions.toFabricVariant(result))) return 0;
        int amount = insert ? offered.getAmount() - result.getAmount() : result.getAmount();
        if (amount <= 0 || amount > offered.getAmount()) return 0;
        try (Transaction nested = transaction.openNested()) {
            if (!context.exchange(state, nested)) return 0;
            nested.commit();
            return FabricFluidConversions.millibucketsToTransferUnits(amount);
        }
    }

    @Override
    public int getSlotCount() {
        var state = context.read();
        return state == null ? 0 : state.handler().size();
    }

    @Override
    public SingleSlotStorage<FluidVariant> getSlot(int slot) {
        java.util.Objects.checkIndex(slot, getSlotCount());
        return new TankView(slot);
    }

    @Override
    public List<SingleSlotStorage<FluidVariant>> getSlots() {
        List<SingleSlotStorage<FluidVariant>> slots = new ArrayList<>();
        for (int i = 0, count = getSlotCount(); i < count; i++) slots.add(new TankView(i));
        return List.copyOf(slots);
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        return getSlots().stream().<StorageView<FluidVariant>>map(slot -> slot).iterator();
    }

    private final class TankView implements SingleSlotStorage<FluidVariant> {
        private final int slot;
        private TankView(int slot) { this.slot = slot; }

        @Override
        public long insert(FluidVariant resource, long amount, TransactionContext transaction) {
            return transfer(resource, amount, transaction, true, slot);
        }

        @Override
        public long extract(FluidVariant resource, long amount, TransactionContext transaction) {
            return transfer(resource, amount, transaction, false, slot);
        }

        @Override
        public FluidVariant getResource() {
            var state = context.read();
            return state == null || slot >= state.handler().size() ? FluidVariant.blank()
                  : FabricFluidConversions.toFabricVariant(state.handler().getFluidInTank(slot, null));
        }

        @Override
        public boolean isResourceBlank() { return getResource().isBlank(); }

        @Override
        public long getAmount() {
            var state = context.read();
            return state == null || slot >= state.handler().size() ? 0
                  : FabricFluidConversions.millibucketsToTransferUnits(state.handler().getFluidInTank(slot, null).getAmount());
        }

        @Override
        public long getCapacity() {
            var state = context.read();
            return state == null || slot >= state.handler().size() ? 0
                  : FabricFluidConversions.millibucketsToTransferUnits(state.handler().getFluidTank(slot, null).getCapacity());
        }
    }
}
