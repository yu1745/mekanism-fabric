package mekanism.common.content.qio;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import mekanism.api.Action;
import mekanism.common.attachments.qio.DriveContents;
import mekanism.common.attachments.qio.DriveMetadata;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.registries.MekanismDataComponents;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class QIODriveData {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism");

    private final QIODriveKey key;
    private final long countCapacity;
    private final int typeCapacity;
    private final Object2LongMap<HashedItem> itemMap = new Object2LongOpenHashMap<>();
    private long itemCount;

    public QIODriveData(QIODriveKey key) {
        this.key = key;
        ItemStack driveStack = key.getDriveStack();
        IQIODriveItem driveItem = (IQIODriveItem) driveStack.getItem();
        // load capacity values
        countCapacity = driveItem.getCountCapacity(driveStack);
        typeCapacity = driveItem.getTypeCapacity(driveStack);
        // load item map from drive stack
        driveStack.getOrDefault(MekanismDataComponents.DRIVE_CONTENTS, DriveContents.EMPTY).loadItemMap(this);
        // update cached item count value
        itemCount = itemMap.values().longStream().sum();

        key.updateMetadata(this);
    }

    public long add(HashedItem type, long amount, Action action) {
        long stored = getStored(type);
        // fail if we've reached item count capacity or adding this item would make us exceed type capacity
        if (itemCount == countCapacity || (stored == 0 && itemMap.size() == typeCapacity)) {
            return amount;
        }
        long toAdd = Math.min(amount, countCapacity - itemCount);
        if (action.execute()) {
            itemMap.put(type, stored + toAdd);
            itemCount += toAdd;
            key.updateMetadata(this);
            key.dataUpdate();
        }
        return amount - toAdd;
    }

    public long remove(HashedItem type, long amount, Action action) {
        return remove(type, amount, action, null);
    }

    /**
     * Transaction-aware removal (Task052 continuation09): the itemMap/itemCount mutation and the physical metadata
     * component update on the <b>captured actual drive stack</b> happen immediately (provisional state), while the
     * holder {@code dataUpdate}/save notification is deferred to the successful outer commit. Aborts (own or later
     * root abort) restore the captured stack's metadata component and the per-drive itemMap entry/itemCount. A
     * replacement drive at the same numeric slot is never overwritten through a stale snapshot: the restore validates
     * the current stack identity first.
     */
    public long remove(HashedItem type, long amount, Action action, @Nullable TransactionContext tx) {
        long stored = getStored(type);
        long removed = Math.min(amount, stored);
        if (action.execute()) {
            ItemStack capturedStack = null;
            DriveMetadata oldMetadata = null;
            if (tx != null) {
                //Establish the actual holder slot stack identity before mutating; snapshot its metadata component
                capturedStack = key.getDriveStack();
                oldMetadata = capturedStack.get(MekanismDataComponents.DRIVE_METADATA);
            }
            long remaining = stored - removed;
            if (remaining > 0) {
                itemMap.put(type, remaining);
            } else {
                itemMap.removeLong(type);
            }
            itemCount -= removed;
            key.updateMetadata(this);
            if (tx != null) {
                final ItemStack stack = capturedStack;
                final DriveMetadata metadata = oldMetadata;
                final long oldStored = stored;
                final long oldItemCount = itemCount + removed;
                //Defer the holder dataUpdate (DriveArray markForSave) until successful outer commit
                tx.onRootCommit(key::dataUpdate);
                Runnable restore = () -> {
                    if (key.getDriveStack() != stack) {
                        LOGGER.error("Not restoring QIO drive data through a stale slot: the drive at slot {} was replaced.", key.driveSlot());
                        return;
                    }
                    if (oldStored > 0) {
                        itemMap.put(type, oldStored);
                    } else {
                        itemMap.removeLong(type);
                    }
                    itemCount = oldItemCount;
                    stack.set(MekanismDataComponents.DRIVE_METADATA, metadata);
                };
                tx.onAbort(restore);
                tx.onRootAbort(restore);
            } else {
                key.dataUpdate();
            }
        }
        return removed;
    }

    public long getStored(HashedItem type) {
        return itemMap.getOrDefault(type, 0L);
    }

    public Object2LongMap<HashedItem> getItemMap() {
        return itemMap;
    }

    public QIODriveKey getKey() {
        return key;
    }

    public long getCountCapacity() {
        return countCapacity;
    }

    public int getTypeCapacity() {
        return typeCapacity;
    }

    public long getTotalCount() {
        return itemCount;
    }

    public int getTotalTypes() {
        return itemMap.size();
    }

    public record QIODriveKey(IQIODriveHolder holder, int driveSlot) {

        public void save(QIODriveData data) {
            holder.save(driveSlot, data);
        }

        public void dataUpdate() {
            holder.onDataUpdate();
        }

        public void updateMetadata(QIODriveData data) {
            ItemStack stack = getDriveStack();
            if (stack.getItem() instanceof IQIODriveItem) {
                stack.set(MekanismDataComponents.DRIVE_METADATA, new DriveMetadata(data));
            } else {
                LOGGER.error("Tried to update QIO meta values on an invalid ItemStack ({}). Something has gone very wrong!", stack.getItem());
            }
        }

        public ItemStack getDriveStack() {
            return holder.getDriveSlots().get(driveSlot).getStack();
        }
    }
}
