package mekanism.common.lib.inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mekanism.common.Mekanism;
import mekanism.api.inventory.IInventorySlot;
import mekanism.fabric.item.FabricItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
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

    /**
     * Stable real-owner participant (R2): the request plan (per-type totalCount, slotted positions, retained view
     * candidates and ordered itemMap membership) is transactional. A snapshot is taken before the first debit
     * mutation at each nesting depth, so an abort at any enclosing level restores the exact pre-transaction plan —
     * including the case where a debit succeeded in a committed nested transaction and a later enclosing
     * transaction aborts (source storage and plan unwind together). Genuine SnapshotParticipant propagation:
     * no per-call callback compensation.
     */
    private final PlanParticipant planParticipant = new PlanParticipant();

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
            try (Transaction discovery = Transaction.openOuter()) {
                if (storage instanceof SlottedStorage<ItemVariant> slotted) {
                    // count backwards- we start from the bottom of the inventory and go back for consistency
                    for (int slot = slotted.getSlotCount() - 1; slot >= 0; slot--) {
                        SingleSlotStorage<ItemVariant> view = slotted.getSlot(slot);
                        //Record the real slotted position, not an invented index (R2); debit re-resolves the same slot
                        discoverView(ret, view, slot, null, max, finder, discovery);
                    }
                } else {
                    int pseudoSlot = -1;
                    for (StorageView<ItemVariant> view : storage) {
                        discoverView(ret, view, pseudoSlot--, view, max, finder, discovery);
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

    private static void discoverView(HandlerTransitRequest ret, StorageView<ItemVariant> view, int slot, @Nullable StorageView<ItemVariant> retainedView,
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
        if (extracted < 0 || extracted > toUse) {
            throw new IllegalStateException("Source view returned an invalid extraction amount");
        }
        if (extracted == 0) {
            //The view did not actually give back anything; skip this candidate entirely
            return;
        }
        //Record the actual accepted positive amount (R2): a partial positive extraction is still a real candidate
        // for exactly the amount the view actually provided, never discarded and never assumed to be `toUse`
        int accepted = extracted > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) extracted;
        ret.itemMap(hashed).addCandidate(new SourceCandidate(slot, retainedView, accepted));
    }

    /**
     * Records a discovered candidate backed by a real slotted position. The index matches the exposed physical slot
     * order of the slotted sided storage used at debit time.
     */
    public void addItem(ItemStack stack, int slot) {
        if (stack.isEmpty()) {
            return;
        }
        itemMap(HashedItem.create(stack)).addCandidate(new SourceCandidate(slot, null, stack.getCount()));
    }

    /**
     * Identity-checked selected-slot candidate (Task052 R3): connects the selected physical {@link IInventorySlot}
     * identity to its real exposed position in this request's sided provider. If the provider is the slotted view of
     * those very slots (a {@link FabricItemStorage}), the candidate debits through that exact real slot view; if the
     * provider does not expose the selected identity, the candidate is rejected loudly instead of debiting through
     * an unrelated position.
     */
    public void addItemFromSelectedSlot(ItemStack stack, IInventorySlot slot) {
        if (stack.isEmpty()) {
            return;
        }
        if (handler instanceof FabricItemStorage fabricStorage) {
            SingleSlotStorage<ItemVariant> view = fabricStorage.viewOf(slot);
            if (view != null) {
                itemMap(HashedItem.create(stack)).addCandidate(new SourceCandidate(-1, view, stack.getCount()));
                return;
            }
            Mekanism.logger.warn("Selected inventory slot identity is not exposed by the sided provider; not ejecting from it.");
            Mekanism.logger.warn("Slot: {}, Provider: {}", slot.getClass().getName(), handler.getClass().getName());
            return;
        }
        //Not the slotted view of these very slots (for example a foreign provider occupying the bounding
        // position): reject loudly rather than debiting through an unrelated storage (Task052 R3)
        Mekanism.logger.warn("Selected-slot ejection provider is not the provider of the selected slots; not ejecting.");
        Mekanism.logger.warn("Provider: {}", handler == null ? "null" : handler.getClass().getName());
    }

    /**
     * Records a discovered candidate backed by a retained real {@link StorageView} of an arbitrary (non-slotted)
     * storage. Operation-local only: never persisted as a world capability cache.
     */
    private void addView(ItemStack stack, int count, StorageView<ItemVariant> view) {
        itemMap(HashedItem.create(stack)).addCandidate(new SourceCandidate(-1, view, count));
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

        private final List<SourceCandidate> candidates = new ArrayList<>();

        public HandlerItemData(HashedItem itemType) {
            super(itemType);
        }

        void addCandidate(SourceCandidate candidate) {
            //Real slotted positions and retained real views are recorded as distinct ordered candidates (R2); a
            // repeated real position accumulates instead of overwriting. Only the newly contributed amount joins
            // totalCount: the merged candidate replaces the old one, so adding the merged count again would
            // double-count the previous contribution (existing 5 + new 3 must total 8, not 13)
            int contributed = candidate.count();
            if (candidate.view() == null && candidate.slot() >= 0) {
                for (Iterator<SourceCandidate> iter = candidates.iterator(); iter.hasNext(); ) {
                    SourceCandidate existing = iter.next();
                    if (existing.view() == null && existing.slot() == candidate.slot()) {
                        iter.remove();
                        candidate = new SourceCandidate(candidate.slot(), null, existing.count() + contributed);
                        break;
                    }
                }
            }
            candidates.add(candidate);
            totalCount += contributed;
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
         * {@code HandlerItemData.use} semantics), not the extracted contents. The request plan joins the caller
         * transaction through the stable {@link PlanParticipant} before any mutation, so a failed candidate or any
         * later enclosing abort restores the exact original plan alongside the source storage. If a real source
         * position does not give back exactly the predicted amount/type, the mismatch is logged loudly and the thrown
         * {@link IllegalStateException} aborts the caller transaction (rolling back target insertion, route and
         * request changes with it).
         */
        @Override
        public ItemStack use(int amount, Transaction tx) {
            if (amount < 0 || amount > totalCount) {
                throw new IllegalArgumentException("Debit amount is outside the request plan");
            }
            Storage<ItemVariant> handler = getHandler();
            if (amount > 0) {
                if (handler == null) {
                    //No real source behind this plan: an attempted debit is a hard failure, never a silent success
                    Mekanism.logger.error("HandlerTransitRequest debit requested without a real source storage.");
                    throw new IllegalStateException("HandlerTransitRequest debit with null source storage");
                }
                HashedItem itemType = getItemType();
                ItemStack itemStack = itemType.getInternalStack();
                ItemVariant variant = ItemVariant.of(itemStack);
                //Snapshot the whole request plan before the first mutation (R2): plan and source unwind together
                HandlerTransitRequest.this.planParticipant.updateSnapshots(tx);
                boolean slottedPlan = candidates.stream().noneMatch(candidate -> candidate.view() != null);
                SlottedStorage<ItemVariant> slotted = null;
                if (slottedPlan && candidates.stream().anyMatch(candidate -> candidate.slot() >= 0)) {
                    if (!(handler instanceof SlottedStorage<ItemVariant> sl)) {
                        throw new IllegalStateException("HandlerTransitRequest's slotted source plan does not match its non-slotted storage "
                              + handler.getClass().getName());
                    }
                    slotted = sl;
                }
                for (Iterator<SourceCandidate> iterator = candidates.iterator(); iterator.hasNext() && amount > 0; ) {
                    SourceCandidate candidate = iterator.next();
                    int toUse = (int) Math.min(amount, candidate.count());
                    long ret;
                    if (candidate.view() != null) {
                        ret = candidate.view().extract(variant, toUse, tx);
                    } else if (slotted != null) {
                        if (candidate.slot() < 0 || candidate.slot() >= slotted.getSlotCount()) {
                            throw new IllegalStateException("HandlerTransitRequest source slot " + candidate.slot() + " no longer exists on "
                                  + handler.getClass().getName());
                        }
                        ret = slotted.getSlot(candidate.slot()).extract(variant, toUse, tx);
                    } else {
                        throw new IllegalStateException("HandlerTransitRequest candidate has neither a retained view nor a slotted source plan");
                    }
                    if (ret != toUse) {// be loud if a prediction doesn't line up, and abort the caller transaction
                        Mekanism.logger.warn("An inventory's returned content does not line up with HandlerTransitRequest's prediction.");
                        Mekanism.logger.warn("HandlerTransitRequest item: {}, toUse: {}, ret: {}, candidate: {}", itemStack, toUse, ret, candidate);
                        Mekanism.logger.warn("ItemStorage: {}", handler.getClass().getName());
                        throw new IllegalStateException("HandlerTransitRequest exact source debit mismatch");
                    }
                    amount -= toUse;
                    totalCount -= toUse;
                    if (totalCount == 0) {
                        itemMap.remove(itemType);
                    }
                    if (candidate.count() - toUse > 0) {
                        //Keep the remaining planned amount on the same real position/view, in place
                        candidate.consume(toUse);
                    } else {
                        iterator.remove();
                    }
                }
                if (amount > 0) {
                    //Candidates exhausted before the requested debit was covered: never report success with an
                    // uncovered remainder; abort the caller transaction (which also restores the plan snapshot)
                    Mekanism.logger.warn("HandlerTransitRequest ran out of source candidates with {} still to debit.", amount);
                    Mekanism.logger.warn("HandlerTransitRequest item: {}, Storage: {}", itemType.getInternalStack(), handler.getClass().getName());
                    throw new IllegalStateException("HandlerTransitRequest source candidates exhausted before covering debit");
                }
            }
            return getStack();
        }
    }

    /**
     * One real debit position: either a real slotted index of the slotted sided source storage, or an operation-local
     * retained real {@link StorageView} of an arbitrary storage (never both, never neither).
     */
    private static final class SourceCandidate {

        private final int slot;
        private final StorageView<ItemVariant> view;
        private int count;

        SourceCandidate(int slot, @Nullable StorageView<ItemVariant> view, int count) {
            this.slot = slot;
            this.view = view;
            this.count = count;
        }

        int slot() {
            return slot;
        }

        @Nullable
        StorageView<ItemVariant> view() {
            return view;
        }

        int count() {
            return count;
        }

        void consume(int amount) {
            count -= amount;
        }
    }

    /**
     * Identity-preserving snapshot of the request plan: the original ordered {@link HandlerItemData} objects with
     * their totalCount plus copies of every ordered candidate (real slot index or retained view reference with its
     * planned count). Restore reinstates those same objects into the map, so a previously captured
     * {@link TransitResponse} (or any held {@link HandlerItemData} reference) observes the restoration after a full
     * debit and enclosing abort instead of retaining a detached consumed object.
     */
    private static final class PlanSnapshot {

        private final Map<HashedItem, List<SourceCandidate>> candidates;
        private final Map<HashedItem, HandlerItemData> identities;
        private final Map<HashedItem, Integer> totals;

        private PlanSnapshot(Map<HashedItem, List<SourceCandidate>> candidates, Map<HashedItem, HandlerItemData> identities,
              Map<HashedItem, Integer> totals) {
            this.candidates = candidates;
            this.identities = identities;
            this.totals = totals;
        }

        static PlanSnapshot take(HandlerTransitRequest request) {
            Map<HashedItem, List<SourceCandidate>> copy = new LinkedHashMap<>();
            Map<HashedItem, HandlerItemData> identityCopy = new LinkedHashMap<>();
            Map<HashedItem, Integer> totals = new LinkedHashMap<>();
            request.itemMap.forEach((hashed, data) -> {
                List<SourceCandidate> list = new ArrayList<>(data.candidates.size());
                for (SourceCandidate candidate : data.candidates) {
                    list.add(new SourceCandidate(candidate.slot(), candidate.view(), candidate.count()));
                }
                copy.put(hashed, list);
                identityCopy.put(hashed, data);
                totals.put(hashed, data.totalCount);
            });
            return new PlanSnapshot(copy, identityCopy, totals);
        }

        void restore(HandlerTransitRequest request) {
            Map<HashedItem, HandlerItemData> restored = new LinkedHashMap<>();
            identities.forEach((hashed, data) -> {
                //Reinstate the exact original object identity: entries fully consumed (and removed from the live
                // map) reappear as the same object a captured response may still reference
                List<SourceCandidate> list = candidates.get(hashed);
                data.candidates.clear();
                for (SourceCandidate candidate : list) {
                    data.candidates.add(new SourceCandidate(candidate.slot(), candidate.view(), candidate.count()));
                }
                data.totalCount = totals.get(hashed);
                restored.put(hashed, data);
            });
            request.itemMap = restored;
        }
    }

    /**
     * Stable plan owner participant (see field {@link #planParticipant}). Uses the real Fabric
     * {@link SnapshotParticipant} nesting-depth snapshot promotion, so rollback propagates through committed
     * nested transactions whose enclosing transaction later aborts, and nothing happens on final commit.
     */
    private final class PlanParticipant extends SnapshotParticipant<PlanSnapshot> {

        @Override
        protected PlanSnapshot createSnapshot() {
            return PlanSnapshot.take(HandlerTransitRequest.this);
        }

        @Override
        protected void readSnapshot(PlanSnapshot snapshot) {
            snapshot.restore(HandlerTransitRequest.this);
        }
    }
}
