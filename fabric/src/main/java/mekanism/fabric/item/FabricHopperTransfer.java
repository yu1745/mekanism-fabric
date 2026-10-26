package mekanism.fabric.item;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla-hopper automation bridge for Mekanism machines.
 *
 * <p>Official NeoForge patches the hopper to consult the machine's item capability; vanilla hoppers on Fabric only
 * understand the {@code Container}/{@code WorldlyContainerHolder} interfaces, whose live-stack mutation protocol
 * ({@code getItem} merged through {@code grow}/{@code shrink}) cannot be mapped onto Mekanism slot APIs without
 * bypassing slot validation and change notification. This bridge therefore routes the two hopper transfer entry
 * points through the exact same transactional surface the Mekanism transporters use - the machine's
 * {@link ItemStorage#SIDED} view ({@link FabricItemStorage}), re-resolved on every attempt like every other
 * Mekanism acceptor - one item per operation at vanilla cadence:</p>
 *
 * <ul>
 * <li><b>Eject</b> (hopper pushes into the block it faces): the machine side is the hopper facing's opposite.</li>
 * <li><b>Suck</b> (hopper pulls from the block above): the machine side is {@code DOWN}.</li>
 * </ul>
 *
 * <p>The guard is deliberately strict: only {@link FabricItemStorage} instances are bridged, so vanilla containers
 * (chests, other hoppers), entity containers, item pickup and any third-party storage keep untouched vanilla
 * behavior. Simulate-before-commit within one transaction means a full or non-accepting machine loses nothing.</p>
 */
public final class FabricHopperTransfer {

    private FabricHopperTransfer() {
    }

    /**
     * Attempts to move one item between a hopper inventory and the Mekanism machine at {@code targetPos}.
     *
     * @param eject {@code true} when the hopper pushes into the machine, {@code false} when it pulls from it
     * @return {@code true} if a Mekanism machine was present and one item was transferred
     */
    public static boolean moveOneItem(Level level, BlockPos targetPos, Direction side, Container hopper, boolean eject) {
        if (level.isClientSide) {
            return false;
        }
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, targetPos, side);
        if (!(storage instanceof FabricItemStorage)) {
            //Vanilla containers and third-party storages stay on the untouched vanilla paths
            return false;
        }
        return eject ? ejectOneItem(hopper, storage) : suckOneItem(hopper, (FabricItemStorage) storage);
    }

    private static boolean ejectOneItem(Container hopper, Storage<ItemVariant> machine) {
        for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
            ItemStack stack = hopper.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            try (Transaction transaction = Transaction.openOuter()) {
                //Vanilla hopper cadence moves exactly one item per attempt; the commit decides, the close aborts
                if (machine.insert(ItemVariant.of(stack), 1, transaction) == 1) {
                    transaction.commit();
                    hopper.removeItem(slot, 1);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean suckOneItem(Container hopper, FabricItemStorage machine) {
        for (StorageView<ItemVariant> view : machine) {
            if (view.getAmount() <= 0 || view.getResource().isBlank()) {
                continue;
            }
            ItemVariant variant = view.getResource();
            try (Transaction transaction = Transaction.openOuter()) {
                if (view.extract(variant, 1, transaction) == 1) {
                    //Route through vanilla's hopper insertion so hopper slot policy stays official; the abort
                    //below restores the extracted item if the hopper cannot accept it
                    if (HopperBlockEntity.addItem(null, hopper, variant.toStack(), null).isEmpty()) {
                        transaction.commit();
                        return true;
                    }
                    return false;
                }
            }
        }
        return false;
    }
}
