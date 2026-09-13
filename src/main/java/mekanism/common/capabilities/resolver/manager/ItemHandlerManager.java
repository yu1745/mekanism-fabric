package mekanism.common.capabilities.resolver.manager;

import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.fabric.item.FabricItemLookup;
import mekanism.fabric.item.FabricItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class to make reading instead of having as messy generics
 *
 * <p>Fabric port: the exposed type is the transaction {@link Storage} view created through the {@link FabricItemLookup} seam, not a Mekanism domain handler. The
 * base handler surface is narrowed from the official {@code ISidedItemHandler} to {@link IMekanismInventory}, which the only current consumer
 * ({@code TileEntityMekanism}) implements; the null side is internal and deliberately resolves to no external Storage, matching the accepted adapters.</p>
 */
public class ItemHandlerManager extends CapabilityHandlerManager<IInventorySlotHolder, IInventorySlot, Storage<ItemVariant>, IMekanismInventory> {

    public ItemHandlerManager(@Nullable IInventorySlotHolder holder, @NotNull IMekanismInventory baseHandler) {
        super(holder, baseHandler, ItemStorage.SIDED, FabricItemLookup::expose, IInventorySlotHolder::getInventorySlots);
    }

    @Override
    protected void revokeHandler(Storage<ItemVariant> handler) {
        if (handler instanceof FabricItemStorage storage) {
            //Owner obligation from the FabricItemLookup seam: permanently fail close the stable side view when its cache entry is dropped
            storage.invalidate();
        }
    }
}
