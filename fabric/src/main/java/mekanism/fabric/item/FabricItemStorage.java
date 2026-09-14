package mekanism.fabric.item;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.common.capabilities.holder.IHolder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Physical-side-bound transactional Fabric view of one stable Mekanism inventory.
 *
 * <p>The exact slot objects and their order are captured at construction. A later size, identity, order, or
 * {@link IMekanismInventory#hasInventory()} change permanently invalidates this view. The owner must additionally
 * call {@link #invalidate()} on removal, reload, facing or side-configuration changes, and every known topology
 * change. Retained aggregate and single-slot views then fail closed.
 *
 * <p>Each mutation first obtains a deterministic per-slot simulation plan. Backing {@link IInventorySlot}
 * implementations must honor {@link Action#SIMULATE} by neither mutating nor notifying; this adapter relies on that
 * API precondition and does not snapshot around simulation, so it cannot prevent or undo side effects from a
 * violating implementation. It then executes the plan in a nested Fabric transaction after taking one participant
 * snapshot containing defensive copies of every captured stack. Execution commits only when return values and
 * complete read-back state exactly match the simulated item, components, counts, and total. Abort restores every
 * captured slot and reads every one back; setter, getter, or mismatch failures are aggregated and propagated after
 * all restore attempts. Immediate dirty, listener, save, or synchronization effects caused by execute or restore
 * are observable and cannot be rolled back by this adapter.
 *
 * <p>Fabric amounts are {@code long}; each Mekanism call is capped to {@link Integer#MAX_VALUE}, and totals are
 * accumulated with saturating arithmetic before being checked against the request. No mutable {@link ItemStack}
 * owned by a caller or backing slot is exposed.
 */
public final class FabricItemStorage extends SnapshotParticipant<List<ItemStack>> implements SlottedStorage<ItemVariant> {

    private final IMekanismInventory handler;
    private final Direction exposureSide;
    @Nullable
    private final IHolder holder;
    private final List<IInventorySlot> capturedSlots;
    private final List<SingleSlotStorage<ItemVariant>> views;
    private volatile boolean invalidated;

    FabricItemStorage(IMekanismInventory handler, Direction exposureSide, @Nullable IHolder holder,
          List<IInventorySlot> exposedSlots) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.exposureSide = Objects.requireNonNull(exposureSide, "exposureSide");
        this.holder = holder;
        capturedSlots = List.copyOf(exposedSlots);
        if (capturedSlots.isEmpty()) {
            throw new IllegalArgumentException("At least one exposed inventory slot is required");
        }
        List<SingleSlotStorage<ItemVariant>> slotViews = new ArrayList<>(capturedSlots.size());
        for (int slot = 0; slot < capturedSlots.size(); slot++) {
            slotViews.add(new SlotView(slot));
        }
        views = List.copyOf(slotViews);
    }

    /**
     * Permanently invalidates this side view. Existing iterators and slot views remain safe, stable objects but no
     * longer expose contents or permit transfer.
     */
    public void invalidate() {
        invalidated = true;
    }

    /**
     * Identity-checked real-slot selection facility (Task052 R3): returns the exposed single-slot view whose backing
     * slot <b>is</b> the given slot object identity, or {@code null} if this view does not expose that slot. Callers
     * use this to connect a selected physical {@link IInventorySlot} identity to its real position in this provider
     * instead of inferring identity from generic indices.
     */
    @Nullable
    public SingleSlotStorage<ItemVariant> viewOf(IInventorySlot slot) {
        if (!hasStableTopology()) {
            return null;
        }
        for (int slotIndex = 0; slotIndex < capturedSlots.size(); slotIndex++) {
            if (capturedSlots.get(slotIndex) == slot) {
                return views.get(slotIndex);
            }
        }
        return null;
    }

    @Override
    public boolean supportsInsertion() {
        return canTransfer(true);
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        return transfer(resource, maxAmount, transaction, true, 0, capturedSlots.size());
    }

    @Override
    public boolean supportsExtraction() {
        return canTransfer(false);
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        return transfer(resource, maxAmount, transaction, false, 0, capturedSlots.size());
    }

    @Override
    public int getSlotCount() {
        return capturedSlots.size();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        return views.get(slot);
    }

    @Override
    public List<SingleSlotStorage<ItemVariant>> getSlots() {
        return views;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Iterator<StorageView<ItemVariant>> iterator() {
        // Every element implements StorageView<ItemVariant>; the immutable list also forbids iterator removal.
        return (Iterator) views.iterator();
    }

    @Override
    protected List<ItemStack> createSnapshot() {
        return copyCapturedStacks();
    }

    @Override
    protected void readSnapshot(List<ItemStack> snapshot) {
        Throwable failure = null;
        for (int slot = 0; slot < capturedSlots.size(); slot++) {
            IInventorySlot inventorySlot = capturedSlots.get(slot);
            ItemStack expected = snapshot.get(slot);
            try {
                inventorySlot.setStack(copyStack(expected));
            } catch (Throwable setterFailure) {
                failure = collectRestoreFailure(failure, setterFailure);
            }
            try {
                ItemStack restored = copySlotStack(inventorySlot, slot);
                if (!sameStack(expected, restored)) {
                    failure = collectRestoreFailure(failure, new IllegalStateException(
                          "Inventory slot " + slot + " restored " + describe(restored) + " instead of "
                                + describe(expected)));
                }
            } catch (Throwable readFailure) {
                failure = collectRestoreFailure(failure, readFailure);
            }
        }
        if (failure != null) {
            throw new IllegalStateException("Failed to restore one or more captured inventory slots", failure);
        }
    }

    private long transfer(ItemVariant resource, long maxAmount, TransactionContext transaction, boolean insertion,
          int firstSlot, int lastSlot) {
        Objects.requireNonNull(resource, "resource");
        StoragePreconditions.notNegative(maxAmount);
        Objects.requireNonNull(transaction, "transaction");
        if (resource.isBlank() || maxAmount == 0 || !canTransfer(insertion)) {
            return 0;
        }

        int[] plan = new int[capturedSlots.size()];
        long simulated = simulatePlan(resource, maxAmount, insertion, firstSlot, lastSlot, plan);
        if (simulated <= 0) {
            return 0;
        }

        try (Transaction nested = Transaction.openNested(transaction)) {
            if (!canTransfer(insertion)) {
                return 0;
            }
            List<ItemStack> expected = copyCapturedStacks();
            if (!canTransfer(insertion)) {
                return 0;
            }
            updateSnapshots(nested);
            if (!canTransfer(insertion)) {
                return 0;
            }

            long executed = 0;
            for (int slot = firstSlot; slot < lastSlot; slot++) {
                int planned = plan[slot];
                if (planned == 0) {
                    continue;
                }
                if (!canTransfer(insertion)) {
                    return 0;
                }
                IInventorySlot inventorySlot = capturedSlots.get(slot);
                ItemStack before = expected.get(slot);
                ItemStack expectedAfter;
                if (isCreativeNow(inventorySlot)) {
                    //Creative policy (operation-specific, actual creative state only): extraction does not deplete
                    // and external insertion voids, so the expected state of this slot is unchanged
                    expectedAfter = before;
                } else if (insertion) {
                    expectedAfter = expectedAfterInsertion(before, resource, planned);
                } else {
                    expectedAfter = expectedAfterExtraction(before, resource, planned);
                }
                if (expectedAfter == null) {
                    return 0;
                }

                int moved;
                if (insertion) {
                    ItemStack remainder = Objects.requireNonNull(inventorySlot.insertItem(
                          resource.toStack(planned), Action.EXECUTE, AutomationType.handler(exposureSide)),
                          "Inventory slot returned a null insertion remainder");
                    moved = insertedAmount(resource, planned, remainder);
                } else {
                    ItemStack extracted = Objects.requireNonNull(inventorySlot.extractItem(
                          planned, Action.EXECUTE, AutomationType.handler(exposureSide)),
                          "Inventory slot returned a null extracted stack");
                    moved = extractedAmount(resource, planned, extracted);
                }
                if (moved != planned) {
                    return 0;
                }
                expected.set(slot, expectedAfter);
                executed = addClamped(executed, moved);
                if (executed > simulated || !canTransfer(insertion)) {
                    return 0;
                }
            }

            if (executed != simulated || !canTransfer(insertion) || !matchesCurrentState(expected)) {
                return 0;
            }
            nested.commit();
            return executed;
        }
    }

    private long simulatePlan(ItemVariant resource, long maxAmount, boolean insertion, int firstSlot, int lastSlot,
          int[] plan) {
        long simulated = 0;
        for (int slot = firstSlot; slot < lastSlot && simulated < maxAmount; slot++) {
            if (!canTransfer(insertion)) {
                return -1;
            }
            if (!insertion) {
                ItemStack current = copySlotStack(capturedSlots.get(slot), slot);
                if (!hasStableTopology()) {
                    return -1;
                }
                if (current.isEmpty() || !resource.matches(current)) {
                    continue;
                }
            }
            int offered = (int) Math.min((long) Integer.MAX_VALUE, maxAmount - simulated);
            IInventorySlot inventorySlot = capturedSlots.get(slot);
            int moved;
            if (insertion) {
                ItemStack remainder = Objects.requireNonNull(inventorySlot.insertItem(
                      resource.toStack(offered), Action.SIMULATE, AutomationType.handler(exposureSide)),
                      "Inventory slot returned a null insertion remainder");
                moved = insertedAmount(resource, offered, remainder);
            } else {
                ItemStack extracted = Objects.requireNonNull(inventorySlot.extractItem(
                      offered, Action.SIMULATE, AutomationType.handler(exposureSide)),
                      "Inventory slot returned a null extracted stack");
                moved = extractedAmount(resource, offered, extracted);
            }
            if (moved < 0) {
                return -1;
            }
            plan[slot] = moved;
            simulated = addClamped(simulated, moved);
            if (simulated > maxAmount || !canTransfer(insertion)) {
                return -1;
            }
        }
        return simulated;
    }

    private boolean canTransfer(boolean insertion) {
        if (!hasStableTopology()) {
            return false;
        }
        boolean allowed = holder == null || (insertion ? holder.canInsert(exposureSide) : holder.canExtract(exposureSide));
        return allowed && hasStableTopology();
    }

    private boolean hasStableTopology() {
        if (invalidated) {
            return false;
        }
        if (!handler.hasInventory()) {
            invalidated = true;
            return false;
        }
        List<IInventorySlot> current = handler.getInventorySlots(exposureSide);
        if (current.size() != capturedSlots.size()) {
            invalidated = true;
            return false;
        }
        for (int slot = 0; slot < capturedSlots.size(); slot++) {
            if (current.get(slot) != capturedSlots.get(slot)) {
                invalidated = true;
                return false;
            }
        }
        return !invalidated;
    }

    private List<ItemStack> copyCapturedStacks() {
        List<ItemStack> copies = new ArrayList<>(capturedSlots.size());
        for (int slot = 0; slot < capturedSlots.size(); slot++) {
            copies.add(copySlotStack(capturedSlots.get(slot), slot));
        }
        return copies;
    }

    private boolean matchesCurrentState(List<ItemStack> expected) {
        boolean matches = true;
        for (int slot = 0; slot < capturedSlots.size(); slot++) {
            ItemStack current = copySlotStack(capturedSlots.get(slot), slot);
            matches &= sameStack(expected.get(slot), current);
            if (!hasStableTopology()) {
                return false;
            }
        }
        return matches;
    }

    private static int insertedAmount(ItemVariant resource, int offered, ItemStack remainder) {
        int remaining = remainder.getCount();
        if (remainder.isEmpty()) {
            return remaining == 0 ? offered : -1;
        }
        if (remaining < 0 || remaining > offered || !resource.matches(remainder)) {
            return -1;
        }
        return offered - remaining;
    }

    private static int extractedAmount(ItemVariant resource, int offered, ItemStack extracted) {
        int amount = extracted.getCount();
        if (extracted.isEmpty()) {
            return amount == 0 ? 0 : -1;
        }
        if (amount < 1 || amount > offered || !resource.matches(extracted)) {
            return -1;
        }
        return amount;
    }

    @Nullable
    private static ItemStack expectedAfterInsertion(ItemStack before, ItemVariant resource, int amount) {
        if (before.isEmpty()) {
            return resource.toStack(amount);
        }
        if (!resource.matches(before)) {
            return null;
        }
        long count = (long) before.getCount() + amount;
        return count <= Integer.MAX_VALUE ? before.copyWithCount((int) count) : null;
    }

    @Nullable
    private static ItemStack expectedAfterExtraction(ItemStack before, ItemVariant resource, int amount) {
        if (before.isEmpty() || !resource.matches(before) || amount > before.getCount()) {
            return null;
        }
        int remaining = before.getCount() - amount;
        return remaining == 0 ? ItemStack.EMPTY : before.copyWithCount(remaining);
    }

    private static ItemStack copySlotStack(IInventorySlot slot, int index) {
        return copyStack(Objects.requireNonNull(slot.getStack(), "Inventory slot " + index + " returned a null stack"));
    }

    private static ItemStack copyStack(ItemStack stack) {
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }

    private static String describe(ItemStack stack) {
        return ItemVariant.of(stack) + " x " + stack.getCount();
    }

    private static long addClamped(long total, int amount) {
        return amount <= 0 ? total : amount > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + amount;
    }

    private static Throwable collectRestoreFailure(@Nullable Throwable failure, Throwable restoreFailure) {
        if (failure == null) {
            return restoreFailure;
        }
        if (failure != restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
        return failure;
    }

    private final class SlotView implements SingleSlotStorage<ItemVariant> {

        private final int slot;

        private SlotView(int slot) {
            this.slot = slot;
        }

        @Override
        public boolean supportsInsertion() {
            return FabricItemStorage.this.supportsInsertion();
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return transfer(resource, maxAmount, transaction, true, slot, slot + 1);
        }

        @Override
        public boolean supportsExtraction() {
            return FabricItemStorage.this.supportsExtraction();
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return transfer(resource, maxAmount, transaction, false, slot, slot + 1);
        }

        @Override
        public boolean isResourceBlank() {
            return getResource().isBlank();
        }

        @Override
        public ItemVariant getResource() {
            if (!hasStableTopology()) {
                return ItemVariant.blank();
            }
            ItemStack stack = copySlotStack(capturedSlots.get(slot), slot);
            return hasStableTopology() && !stack.isEmpty() ? ItemVariant.of(stack) : ItemVariant.blank();
        }

        @Override
        public long getAmount() {
            if (!hasStableTopology()) {
                return 0;
            }
            int amount = copySlotStack(capturedSlots.get(slot), slot).getCount();
            return hasStableTopology() ? Math.max(0, amount) : 0;
        }

        @Override
        public long getCapacity() {
            if (!hasStableTopology()) {
                return 0;
            }
            IInventorySlot inventorySlot = capturedSlots.get(slot);
            ItemStack stored = copySlotStack(inventorySlot, slot);
            int capacity = inventorySlot.getLimit(stored.isEmpty() ? ItemStack.EMPTY : stored);
            return hasStableTopology() ? Math.max(0, capacity) : 0;
        }
    }
}
