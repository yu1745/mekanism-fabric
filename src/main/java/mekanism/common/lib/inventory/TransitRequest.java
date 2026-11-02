package mekanism.common.lib.inventory;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import mekanism.api.text.EnumColor;
import mekanism.common.capabilities.item.TransporterItemStorage;
import mekanism.common.content.network.transmitter.LogisticalTransporterBase;
import mekanism.common.lib.inventory.TransitRequest.ItemData;
import mekanism.common.util.StackUtils;
import mekanism.common.util.WorldUtils;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port: requests operate on real sided {@link Storage} endpoints instead of NeoForge
 * {@code IItemHandler} integer slots. Target insertion and source debit are caller-owned Fabric transactions: the
 * transaction-context overloads stage insertion into the given transaction and capture the accepted amount in the
 * returned {@link TransitResponse} before any debit happens; the caller then executes the exact debit
 * ({@link #TransitResponse#useAll(Transaction)}) and commits. A response never carries an open transaction.
 */
public abstract class TransitRequest implements Iterable<ItemData> {

    private final TransitResponse EMPTY = new TransitResponse(ItemStack.EMPTY, null);

    public static SimpleTransitRequest simple(ItemStack stack) {
        return new SimpleTransitRequest(stack);
    }

    public static HandlerTransitRequest anyItem(Storage<ItemVariant> inventory, int amount) {
        return definedItem(inventory, amount, Finder.ANY);
    }

    public static HandlerTransitRequest definedItem(Storage<ItemVariant> inventory, int amount, Finder finder) {
        return definedItem(inventory, 1, amount, finder);
    }

    /**
     * Discovers the real available contents of the given sided {@link Storage} up to {@code max} of each matching item
     * type (at least {@code min}, or the type is dropped), counting backwards over real slotted positions where the
     * storage is slotted. Discovery simulation is cumulative and abort-only: it performs its provisional extractions
     * inside one transaction that is always aborted, so overlapping resources are never counted twice and no state
     * survives discovery.
     */
    public static HandlerTransitRequest definedItem(Storage<ItemVariant> inventory, int min, int max, Finder finder) {
        return HandlerTransitRequest.discover(inventory, min, max, finder);
    }

    @NotNull
    public TransitResponse eject(BlockEntity outputter, @Nullable Storage<ItemVariant> target, int min, Function<LogisticalTransporterBase, EnumColor> outputColor) {
        return eject(outputter, outputter.getBlockPos(), target, min, outputColor);
    }

    /**
     * Transaction-aware eject without an explicit outputter position (the tile's own position is used).
     */
    @NotNull
    public TransitResponse eject(BlockEntity outputter, @Nullable Storage<ItemVariant> target, int min,
          Function<LogisticalTransporterBase, EnumColor> outputColor, Transaction tx) {
        return eject(outputter, outputter.getBlockPos(), target, min, outputColor, tx);
    }

    /**
     * Legacy self-contained eject: opens its own caller transaction, stages insertion and exact source debit and
     * commits. Immediate consumers use {@link #eject(BlockEntity, BlockPos, Storage, int, Function, Transaction)}
     * instead so route/source reservations join the caller transaction.
     */
    @NotNull
    public TransitResponse eject(BlockEntity outputter, BlockPos outputterPos, @Nullable Storage<ItemVariant> target, int min,
          Function<LogisticalTransporterBase, EnumColor> outputColor) {
        try (Transaction tx = Transaction.openOuter()) {
            try {
                TransitResponse response = eject(outputter, outputterPos, target, min, outputColor, tx);
                if (!response.isEmpty()) {
                    response.useAll(tx);
                }
                tx.commit();
                return response;
            } catch (RuntimeException e) {
                //Abort is implicit; surface the failure as "nothing was ejected"
                return getEmptyResponse();
            }
        }
    }

    /**
     * Transaction-aware eject: stages the route/target insertion into {@code tx} and returns the response with the
     * stable accepted amount captured before any debit. The caller performs the exact source debit
     * ({@code response.useAll(tx)}) and commits {@code tx}; a failed debit must abort the whole transaction.
     */
    @NotNull
    public TransitResponse eject(BlockEntity outputter, BlockPos outputterPos, @Nullable Storage<ItemVariant> target, int min,
          Function<LogisticalTransporterBase, EnumColor> outputColor, Transaction tx) {
        if (isEmpty()) {//Short circuit if our request is empty
            return getEmptyResponse();
        } else if (target instanceof TransporterItemStorage endpoint) {
            if (!endpoint.isValid()) {
                return getEmptyResponse();
            }
            LogisticalTransporterBase transporter = endpoint.getTransporter();
            return transporter.insert(outputter, outputterPos, this, outputColor.apply(transporter), true, min, tx);
        }
        return addToInventoryUnchecked(target, min, tx);
    }

    /**
     * Legacy self-contained insertion (used where the caller owns no source debit, e.g. final in-flight delivery that
     * only needs the target half staged; transporter delivery passes its own transaction).
     */
    @NotNull
    public TransitResponse addToInventory(Level level, BlockPos pos, @Nullable Storage<ItemVariant> inventory, int min, boolean force) {
        try (Transaction tx = Transaction.openOuter()) {
            try {
                TransitResponse response = addToInventory(level, pos, inventory, min, force, tx);
                tx.commit();
                return response;
            } catch (RuntimeException e) {
                return getEmptyResponse();
            }
        }
    }

    @NotNull
    public TransitResponse addToInventory(Level level, BlockPos pos, @Nullable Storage<ItemVariant> inventory, int min, boolean force, Transaction tx) {
        if (isEmpty()) {//Short circuit if our request is empty
            return getEmptyResponse();
        } else if (force && WorldUtils.getTileEntity(level, pos) instanceof IAdvancedTransportEjector sorter) {
            return sorter.sendHome(this, tx);
        }
        return addToInventoryUnchecked(inventory, min, tx);
    }

    //Note: We are unchecked because we don't validate if we are empty or not
    @NotNull
    public TransitResponse addToInventoryUnchecked(@Nullable Storage<ItemVariant> inventory, int min) {
        try (Transaction tx = Transaction.openOuter()) {
            try {
                TransitResponse response = addToInventoryUnchecked(inventory, min, tx);
                tx.commit();
                return response;
            } catch (RuntimeException e) {
                return getEmptyResponse();
            }
        }
    }

    /**
     * Transaction-aware insertion. When {@code min > 1}, the minimum is enforced against the <b>actual</b> accepted
     * amount inside a nested candidate transaction that aborts under-min, instead of an unrelated simulate/execute
     * sequence. Accepted candidates merge into {@code tx}; nothing is committed by this method.
     */
    @NotNull
    public TransitResponse addToInventoryUnchecked(@Nullable Storage<ItemVariant> inventory, int min, Transaction tx) {
        if (inventory == null) {
            return getEmptyResponse();
        }
        if (!inventory.supportsInsertion()) {
            //If the inventory can't accept anything just exit early
            return getEmptyResponse();
        }
        try (Transaction candidate = Transaction.openNested(tx)) {
            TransitResponse response = insertAll(inventory, min, candidate);
            if (min > 1 && (response.isEmpty() || response.getSendingAmount() < min)) {
                //Abort the candidate: we were not able to actually insert the requested minimum amount
                return getEmptyResponse();//abort is implicit
            }
            candidate.commit();
            return response;
        }
    }

    //Note: Unchecked: caller validates empty/inventory
    @NotNull
    private TransitResponse insertAll(Storage<ItemVariant> inventory, int min, Transaction tx) {
        for (ItemData data : this) {
            ItemVariant variant = ItemVariant.of(data.getStack());
            int count = data.getTotalCount();
            if (variant.isBlank() || count <= 0) {
                continue;
            }
            long accepted = inventory.insert(variant, count, tx);
            if (accepted > 0) {
                if (accepted > Integer.MAX_VALUE) {
                    accepted = Integer.MAX_VALUE;
                }
                //Capture the stable accepted amount before any debit happens
                return createResponse(StackUtils.size(data.getStack(), (int) accepted), data);
            }
        }
        return getEmptyResponse();
    }

    public boolean isEmpty() {
        return !iterator().hasNext();
    }

    @NotNull
    public TransitResponse createResponse(ItemStack inserted, ItemData data) {
        return new TransitResponse(inserted, data);
    }

    @NotNull
    public TransitResponse createSimpleResponse() {
        for (ItemData data : this) {
            return createResponse(data.getStack(), data);
        }
        return getEmptyResponse();
    }

    @NotNull
    public TransitResponse getEmptyResponse() {
        return EMPTY;
    }

    public static class TransitResponse {

        private final ItemStack inserted;
        private final ItemData slotData;

        public TransitResponse(@NotNull ItemStack inserted, ItemData slotData) {
            this.inserted = inserted;
            this.slotData = slotData;
        }

        public int getSendingAmount() {
            return inserted.getCount();
        }

        public ItemData getSlotData() {
            return slotData;
        }

        public ItemStack getStack() {
            return inserted;
        }

        public boolean isEmpty() {
            return inserted.isEmpty() || slotData.getTotalCount() == 0;
        }

        public ItemStack getRejected() {
            if (isEmpty()) {
                return ItemStack.EMPTY;
            }
            return slotData.getItemType().createStack(slotData.getTotalCount() - getSendingAmount());
        }

        public ItemStack use(int amount) {
            return slotData.use(amount);
        }

        public ItemStack use(Transaction tx) {
            return slotData.use(tx);
        }

        public ItemStack useAll() {
            return use(getSendingAmount());
        }

        public ItemStack useAll(Transaction tx) {
            return use(getSendingAmount(), tx);
        }

        public ItemStack use(int amount, Transaction tx) {
            return slotData.use(amount, tx);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TransitResponse other = (TransitResponse) o;
            return (inserted == other.inserted || ItemStack.matches(inserted, other.inserted)) && slotData.equals(other.slotData);
        }

        @Override
        public int hashCode() {
            int code = ItemStack.hashItemAndComponents(inserted);
            code = 31 * code + inserted.getCount();
            code = 31 * code + slotData.hashCode();
            return code;
        }
    }

    public static class ItemData {

        private final HashedItem itemType;
        protected int totalCount;

        public ItemData(HashedItem itemType) {
            this.itemType = itemType;
        }

        public HashedItem getItemType() {
            return itemType;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public ItemStack getStack() {
            return getItemType().createStack(getTotalCount());
        }

        public ItemStack use(int amount) {
            MekanismEmptyUse();
            return ItemStack.EMPTY;
        }

        public ItemStack use(Transaction tx) {
            return use(getTotalCount(), tx);
        }

        public ItemStack use(int amount, Transaction tx) {
            MekanismEmptyUse();
            return ItemStack.EMPTY;
        }

        private static void MekanismEmptyUse() {
            org.slf4j.LoggerFactory.getLogger("Mekanism").error("Can't 'use' with this type of TransitResponse: {}", ItemData.class.getName());
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ItemData itemData = (ItemData) o;
            return getTotalCount() == itemData.getTotalCount() && getItemType().equals(itemData.getItemType());
        }

        @Override
        public int hashCode() {
            return 31 * getItemType().hashCode() + getTotalCount();
        }
    }

    public static class SimpleTransitRequest extends CollectionTransitRequest {

        private final List<ItemData> slotData;

        protected SimpleTransitRequest(ItemStack stack) {
            slotData = Collections.singletonList(new SimpleItemData(stack));
        }

        @Override
        public List<ItemData> getItemData() {
            return slotData;
        }

        public static class SimpleItemData extends ItemData {

            public SimpleItemData(ItemStack stack) {
                //TODO: Can this use raw to avoid a copy? My intuition says yes as I don't think the item data stays around when the stack can mutate
                // but this definitely needs more thought
                super(HashedItem.create(stack));
                totalCount = stack.getCount();
            }
        }
    }
}
