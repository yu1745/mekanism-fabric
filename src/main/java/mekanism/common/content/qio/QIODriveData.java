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
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class QIODriveData {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism");

    /**
     * Stable real-owner participant (Task052 R4 correction): the drive's itemMap/itemCount and the physical DRIVE_METADATA
     * component on the <b>captured actual drive stack</b> are transactional. A snapshot is taken before the first
     * mutation at each nesting depth; the real {@link SnapshotParticipant} nesting promotion makes an enclosing abort
     * after a committed nested removal roll the drive data back exactly. The holder {@code dataUpdate}
     * (DriveArray markForSave) notification is commit-only ({@code onFinalCommit}) and only fires for removals that
     * survived every nesting level.
     */
    private final DriveStateParticipant driveStateTx = new DriveStateParticipant();

    private final QIODriveKey key;
    private final ItemStack boundDriveStack;
    private final long countCapacity;
    private final int typeCapacity;
    private final Object2LongMap<HashedItem> itemMap = new Object2LongOpenHashMap<>();
    private long itemCount;

    public QIODriveData(QIODriveKey key) {
        this.key = key;
        ItemStack driveStack = key.getDriveStack();
        boundDriveStack = driveStack;
        IQIODriveItem driveItem = (IQIODriveItem) driveStack.getItem();
        // load capacity values
        countCapacity = driveItem.getCountCapacity(driveStack);
        typeCapacity = driveItem.getTypeCapacity(driveStack);
        // load item map from drive stack
        driveStack.getOrDefault(MekanismDataComponents.DRIVE_CONTENTS.get(), DriveContents.EMPTY).loadItemMap(this);
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
     * holder {@code dataUpdate}/save notification is deferred to the successful outer commit through the stable
     * drive participant. Any enclosing abort restores the captured stack's metadata component and the per-drive
     * itemMap entry/itemCount. A replacement drive at the same numeric slot is never silently overwritten: if the
     * identity no longer matches at restore time the violation is surfaced as a loud failure instead of a claimed
     * rollback.
     */
    public long remove(HashedItem type, long amount, Action action, @Nullable TransactionContext tx) {
        long stored = getStored(type);
        long removed = Math.min(amount, stored);
        if (action.execute()) {
            if (tx != null) {
                if (key.getDriveStack() != boundDriveStack) {
                    throw new IllegalStateException("QIO drive owner no longer matches its physical stack");
                }
                //Join the drive's state to the caller transaction BEFORE any mutation, capturing the actual holder
                // slot stack identity and its metadata component at snapshot time (R4)
                driveStateTx.updateSnapshots(tx);
            }
            long remaining = stored - removed;
            if (remaining > 0) {
                itemMap.put(type, remaining);
            } else {
                itemMap.removeLong(type);
            }
            itemCount -= removed;
            key.updateMetadata(this);
            if (tx == null) {
                key.dataUpdate();
            }
        }
        return removed;
    }

    private final class DriveStateParticipant extends SnapshotParticipant<DriveStateSnapshot> {

        @Override
        protected DriveStateSnapshot createSnapshot() {
            return new DriveStateSnapshot(new Object2LongOpenHashMap<>(itemMap), itemCount, boundDriveStack,
                  boundDriveStack.get(MekanismDataComponents.DRIVE_METADATA.get()));
        }

        @Override
        protected void readSnapshot(DriveStateSnapshot snapshot) {
            if (key.getDriveStack() != snapshot.capturedStack) {
                //The drive in this holder slot was replaced during the transaction; restoring through it would
                // corrupt a replacement drive. Surface the unrecoverable identity violation loudly.
                LOGGER.error("QIO drive identity violation during transaction rollback: the drive at slot {} was replaced; in-memory data and the metadata of the current stack may be inconsistent.", key.driveSlot());
                throw new IllegalStateException("QIO drive replaced during transaction; cannot roll back drive data");
            }
            itemMap.clear();
            itemMap.putAll(snapshot.itemMap);
            itemCount = snapshot.itemCount;
            if (snapshot.capturedMetadata != null) {
                snapshot.capturedStack.set(MekanismDataComponents.DRIVE_METADATA.get(), snapshot.capturedMetadata);
            } else {
                snapshot.capturedStack.remove(MekanismDataComponents.DRIVE_METADATA.get());
            }
        }

        @Override
        protected void onFinalCommit() {
            //Commit-only holder notification: the removal survived every nesting level
            key.dataUpdate();
        }
    }

    private record DriveStateSnapshot(Object2LongMap<HashedItem> itemMap, long itemCount, ItemStack capturedStack,
          @Nullable DriveMetadata capturedMetadata) {
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
                stack.set(MekanismDataComponents.DRIVE_METADATA.get(), new DriveMetadata(data));
            } else {
                LOGGER.error("Tried to update QIO meta values on an invalid ItemStack ({}). Something has gone very wrong!", stack.getItem());
            }
        }

        public ItemStack getDriveStack() {
            return holder.getDriveSlots().get(driveSlot).getStack();
        }
    }
}
