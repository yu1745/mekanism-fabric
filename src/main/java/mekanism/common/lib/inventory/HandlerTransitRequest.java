package mekanism.common.lib.inventory;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mekanism.common.Mekanism;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port (Task052 continuation09): the handler is a real sided {@link Storage}, and the source plan records real
 * slotted positions of a {@link SlottedStorage} (matching the exposed physical slot order) or operation-local real
 * {@link StorageView}s for arbitrary storages - never invented stable integer slots. Debit
 * ({@link HandlerItemData#use(int, Transaction)}) extracts through those same real positions inside the caller-owned
 * transaction and verifies the exact debited amount; a mismatch is logged loudly and aborts the caller transaction.
 */
public class HandlerTransitRequest extends CollectionTransitRequest {

    @Nullable
    private final Storage<ItemVariant> handler;
    private Map<HashedItem, HandlerItemData> itemMap = Collections.emptyMap();

    public HandlerTransitRequest(@Nullable Storage<ItemVariant> handler) {
        this.handler = handler;
    }

    /**
     * Cumulative abort-only discovery over the real storage contents, honoring {@code min}/{@code max} per hashed
     * item type and the {@link Finder}. Slotted storages are counted backwards from the bottom real position for
     * consistency with the official behavior; arbitrary storages iterate their real views. All provisional
     * extractions happen in one transaction that is always aborted, so discovery never mutates the source, and
     * overlapping resources can never be counted twice.
     */
    public static HandlerTransitRequest discover(@Nullable Storage<ItemVariant> storage, int min, int max, Finder finder) {
        HandlerTransitRequest ret = new HandlerTransitRequest(storage);
        if (storage != null && storage.supportsExtraction()) {
            try (Transaction discovery = Transaction.openCurrent()) {
                if (storage instanceof SlottedStorage<ItemVariant> slotted) {
                    // count backwards- we start from the bottom of the inventory and go back for consistency
                    for (int slot = slotted.getSlotCount() - 1; slot >= 0; slot--) {
                        SingleSlotStorage<ItemVariant> view = slotted.getSlot(slot);
                        discoverView(ret, view, null, max, finder, discovery);
                    }
                } else {
                    for (StorageView<ItemVariant> view : storage) {
                        discoverView(ret, view, view, max, finder, discovery);
                    }
                }
            }//Never committed: abort rolls back every provisional discovery extraction
        }
        // remove items that we don't have enough of
        for (Iterator<HandlerItemData> iterator = ret.itemMap.values().iterator(); iterator.hasNext(); ) {
            if (iterator.next().getTotalCount() < min) {
                iterator.remove();
            }
        }
        return ret;
    }

    private static void discoverView(HandlerTransitRequest ret, StorageView<ItemVariant> view, @Nullable StorageView<ItemVariant> retainedView,
          int max, Finder finder, Transaction discovery) {
        if (view.isResourceBlank()) {
            return;
        }
        ItemVariant resource = view.getResource();
        long amount = view.getAmount();
        if (amount <= 0) {
            return;
        }
        int reported = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        ItemStack stack = resource.toStack(reported);
        if (!finder.test(stack)) {
            return;
        }
        HashedItem hashed = HashedItem.raw(stack);
        int toUse = Math.min(reported, max - ret.getCount(hashed));
        if (toUse <= 0) {
            return; // continue if we don't need any more of this item type
        }
        //Cumulative provisional extraction: later candidates see earlier ones inside this transaction, so
        // overlapping resources are never counted twice; the transaction is aborted below
        long extracted = view.extract(resource, toUse, discovery);
        if (extracted != toUse) {
            //The view did not actually give back the amount it reported; skip this candidate entirely
            return;
        }
        if (retainedView == null) {
            ret.addItem(stack, toUse, -1);
        } else {
            ret.addView(stack, toUse, retainedView);
        }
    }

    /**
     * Records a discovered candidate backed by a real slotted position. The index matches the exposed physical slot
     * order of the slotted sided storage used at debit time.
     */
    public void addItem(ItemStack stack, int slot) {
        HashedItem hashed = HashedItem.create(stack);
        itemMap(hashed).addSlot(slot, stack);
    }

    private void addItem(ItemStack stack, int count, int slot) {
        itemMap(HashedItem.create(stack)).addAmount(slot, count);
    }

    /**
     * Records a discovered candidate backed by a retained real {@link StorageView} of an arbitrary (non-slotted)
     * storage. Operation-local only: never persisted as a world capability cache.
     */
    private void addView(ItemStack stack, int count, StorageView<ItemVariant> view) {
        itemMap(HashedItem.create(stack)).addView(count, view);
    }

    private HandlerItemData itemMap(HashedItem hashed) {
        if (itemMap.isEmpty()) {
            itemMap = new LinkedHashMap<>();
        }
        return itemMap.computeIfAbsent(hashed, HandlerItemData::new);
    }

    public int getCount(HashedItem itemType) {
        ItemData data = itemMap.get(itemType);
        return data == null ? 0 : data.getTotalCount();
    }

    @Nullable
    protected Storage<ItemVariant> getHandler() {
        return handler;
    }

    @Override
    public Collection<HandlerItemData> getItemData() {
        return itemMap.values();
    }

    @Override
    public boolean isEmpty() {
        //Skip the values call
        return itemMap.isEmpty();
    }

    public class HandlerItemData extends ItemData {

        private final Int2IntMap slotMap = new Int2IntLinkedOpenHashMap();
        private final List<ViewCandidate> views = new ArrayList<>();

        public HandlerItemData(HashedItem itemType) {
            super(itemType);
        }

        public void addSlot(int id, ItemStack stack) {
            slotMap.put(id, stack.getCount());
            totalCount += stack.getCount();
        }

        private void addAmount(int slot, int count) {
            slotMap.put(slot, count);
            totalCount += count;
        }

        private void addView(int count, StorageView<ItemVariant> view) {
            views.add(new ViewCandidate(view, count));
            totalCount += count;
        }

        /**
         * Legacy nontransactional debit: extracts immediately through the real source positions in its own
         * transaction and commits it. Preserved for callers that do not own a transaction; immediate consumers use
         * {@link #use(int, Transaction)}.
         */
        @Override
        public ItemStack use(int amount) {
            try (Transaction tx = Transaction.openOuter()) {
                ItemStack ret = use(amount, tx);
                tx.commit();
                return ret;
            }
        }

        /**
         * Exact source debit inside the caller-owned transaction. Returns the remaining request contents (official
         * {@code HandlerItemData.use} semantics), not the extracted contents. If a real source position does not give
         * back exactly the predicted amount/type, the mismatch is logged loudly and the thrown
         * {@link IllegalStateException} aborts the caller transaction (rolling back target insertion, route and
         * request changes with it).
         */
        @Override
        public ItemStack use(int amount, Transaction tx) {
            Storage<ItemVariant> handler = getHandler();
            if (handler != null && amount > 0) {
                HashedItem itemType = getItemType();
                ItemStack itemStack = itemType.getInternalStack();
                ItemVariant variant = ItemVariant.of(itemStack);
                if (!slotMap.isEmpty()) {
                    if (!(handler instanceof SlottedStorage<ItemVariant> slotted)) {
                        throw new IllegalStateException("HandlerTransitRequest's slotted source plan does not match its non-slotted storage "
                              + handler.getClass().getName());
                    }
                    for (ObjectIterator<Int2IntMap.Entry> iterator = Int2IntMaps.fastIterator(slotMap); iterator.hasNext(); ) {
                        Int2IntMap.Entry entry = iterator.next();
                        int slot = entry.getIntKey();
                        int currentCount = entry.getIntValue();
                        int toUse = Math.min(amount, currentCount);
                        if (slot >= slotted.getSlotCount()) {
                            throw new IllegalStateException("HandlerTransitRequest source slot " + slot + " no longer exists on "
                                  + handler.getClass().getName());
                        }
                        long ret = slotted.getSlot(slot).extract(variant, toUse, tx);
                        if (ret != toUse) {// be loud if a prediction doesn't line up, and abort the caller transaction
                            Mekanism.logger.warn("An inventory's returned content does not line up with HandlerTransitRequest's prediction.");
                            Mekanism.logger.warn("HandlerTransitRequest item: {}, toUse: {}, ret: {}, slot: {}", itemStack, toUse, ret, slot);
                            Mekanism.logger.warn("ItemStorage: {}", handler.getClass().getName());
                            throw new IllegalStateException("HandlerTransitRequest exact source debit mismatch");
                        }
                        amount -= toUse;
                        totalCount -= toUse;
                        if (totalCount == 0) {
                            itemMap.remove(itemType);
                        }
                        currentCount = currentCount - toUse;
                        if (currentCount == 0) {
                            //If we removed all items from this slot, remove the slot
                            iterator.remove();
                        } else {
                            // otherwise, update the amount in it
                            entry.setValue(currentCount);
                        }
                        if (amount == 0) {
                            break;
                        }
                    }
                }
                for (Iterator<ViewCandidate> iterator = views.iterator(); iterator.hasNext() && amount > 0; ) {
                    ViewCandidate candidate = iterator.next();
                    int toUse = Math.min(amount, candidate.count);
                    long ret = candidate.view.extract(variant, toUse, tx);
                    if (ret != toUse) {
                        Mekanism.logger.warn("An inventory's returned content does not line up with HandlerTransitRequest's prediction.");
                        Mekanism.logger.warn("HandlerTransitRequest item: {}, toUse: {}, ret: {}, view: {}", itemStack, toUse, ret, candidate.view);
                        Mekanism.logger.warn("ItemStorage: {}", handler.getClass().getName());
                        throw new IllegalStateException("HandlerTransitRequest exact source debit mismatch");
                    }
                    amount -= toUse;
                    totalCount -= toUse;
                    candidate.count -= toUse;
                    if (totalCount == 0) {
                        itemMap.remove(itemType);
                    }
                    if (candidate.count == 0) {
                        iterator.remove();
                    }
                    if (amount == 0) {
                        break;
                    }
                }
            }
            return getStack();
        }
    }

    /**
     * A real retained {@link StorageView} candidate of a non-slotted storage and the amount planned from it.
     */
    private static final class ViewCandidate {

        private final StorageView<ItemVariant> view;
        private int count;

        ViewCandidate(StorageView<ItemVariant> view, int count) {
            this.view = view;
            this.count = count;
        }
    }
}
