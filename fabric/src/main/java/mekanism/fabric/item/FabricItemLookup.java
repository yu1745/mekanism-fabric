package mekanism.fabric.item;

import java.util.List;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Sole construction seam for physical-side Fabric item-storage views of Mekanism inventories.
 *
 * <p>This class deliberately performs no {@code ItemStorage.SIDED} provider registration and exposes no reverse
 * Fabric-to-Mekanism adapter. A future owner must cache exactly one returned view per physical side and call
 * {@link FabricItemStorage#invalidate()} on removal, reload, facing or side-configuration changes, and any inventory
 * topology change.
 */
public final class FabricItemLookup {

    private FabricItemLookup() {
    }

    /**
     * Creates a stable, physical-side-bound view, or returns {@code null} when there is no external inventory to
     * expose. Null is always an internal Mekanism side and is therefore never exposed through this boundary.
     */
    @Nullable
    public static FabricItemStorage expose(@Nullable IMekanismInventory handler, @Nullable Direction side,
          @Nullable IHolder holder) {
        if (handler == null || side == null || !handler.hasInventory()) {
            return null;
        }
        List<IInventorySlot> slots = handler.getInventorySlots(side);
        if (slots.isEmpty()) {
            return null;
        }
        return new FabricItemStorage(handler, side, holder, slots);
    }
}
