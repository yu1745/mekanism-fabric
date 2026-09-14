package mekanism.fabric.item;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import mekanism.api.Action;
import mekanism.common.attachments.containers.item.ComponentBackedItemHandler;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Actual component-backed inventory slots exposed through a live Fabric container-item transaction. */
public final class FabricComponentItemStorage implements SlottedStorage<ItemVariant> {
    private final ComponentItemContext<ComponentBackedItemHandler> context;

    public FabricComponentItemStorage(ContainerItemContext context, Item item, Function<ItemStack, ComponentBackedItemHandler> provider) {
        this.context = new ComponentItemContext<>(context, item, provider);
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        return transfer(resource, maxAmount, transaction, true, -1);
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        return transfer(resource, maxAmount, transaction, false, -1);
    }

    private long transfer(ItemVariant resource, long maxAmount, TransactionContext transaction, boolean insert, int selected) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (maxAmount == 0) return 0;
        var state = context.read();
        if (state == null || selected >= state.handler().size()) return 0;
        var handler = state.handler();
        long moved = 0;
        int first = selected < 0 ? 0 : selected;
        int end = selected < 0 ? handler.size() : selected + 1;
        for (int slot = first; slot < end && moved < maxAmount; slot++) {
            int offered = (int) Math.min(Integer.MAX_VALUE, maxAmount - moved);
            if (!insert && !resource.matches(handler.getStackInSlot(slot, null))) continue;
            ItemStack result = insert ? handler.insertItem(slot, resource.toStack(offered), null, Action.EXECUTE)
                  : handler.extractItem(slot, offered, null, Action.EXECUTE);
            if (!result.isEmpty() && !resource.matches(result)) return 0;
            int amount = insert ? offered - result.getCount() : result.getCount();
            if (amount < 0 || amount > offered) return 0;
            moved += amount;
        }
        if (moved == 0) return 0;
        try (Transaction nested = transaction.openNested()) {
            if (!context.exchange(state, nested)) return 0;
            nested.commit();
            return moved;
        }
    }

    @Override
    public int getSlotCount() {
        var state = context.read();
        return state == null ? 0 : state.handler().size();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        java.util.Objects.checkIndex(slot, getSlotCount());
        return new SlotView(slot);
    }

    @Override
    public List<SingleSlotStorage<ItemVariant>> getSlots() {
        List<SingleSlotStorage<ItemVariant>> slots = new ArrayList<>();
        for (int i = 0, count = getSlotCount(); i < count; i++) slots.add(new SlotView(i));
        return List.copyOf(slots);
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        return getSlots().stream().<StorageView<ItemVariant>>map(slot -> slot).iterator();
    }

    private final class SlotView implements SingleSlotStorage<ItemVariant> {
        private final int slot;
        private SlotView(int slot) { this.slot = slot; }

        @Override
        public long insert(ItemVariant resource, long amount, TransactionContext transaction) {
            return transfer(resource, amount, transaction, true, slot);
        }

        @Override
        public long extract(ItemVariant resource, long amount, TransactionContext transaction) {
            return transfer(resource, amount, transaction, false, slot);
        }

        @Override
        public ItemVariant getResource() {
            var state = context.read();
            return state == null || slot >= state.handler().size() ? ItemVariant.blank() : ItemVariant.of(state.handler().getStackInSlot(slot, null));
        }

        @Override
        public boolean isResourceBlank() { return getResource().isBlank(); }

        @Override
        public long getAmount() {
            var state = context.read();
            return state == null || slot >= state.handler().size() ? 0 : state.handler().getStackInSlot(slot, null).getCount();
        }

        @Override
        public long getCapacity() {
            var state = context.read();
            return state == null || slot >= state.handler().size() ? 0 : state.handler().getSlotLimit(slot, null);
        }
    }
}
