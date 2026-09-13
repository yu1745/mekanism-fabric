package mekanism.common.capabilities.resolver.manager;

import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.api.inventory.ISidedItemHandler;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.proxy.ProxyItemHandler;
import mekanism.fabric.item.FabricItemLookup;
import mekanism.fabric.item.FabricItemStorage;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class to make reading instead of having as messy generics
 *
 * <p>Fabric port: the exposed type is the transaction {@link Storage} view created through the {@link FabricItemLookup} seam, not a Mekanism domain handler. The
 * base handler surface is narrowed from the official {@code ISidedItemHandler} to {@link IMekanismInventory} (itself an {@code ISidedItemHandler}), which the
 * audited deferred consumer {@code TileEntityMekanism} implements; the null side is internal and deliberately resolves to no external Storage, matching the
 * accepted adapters. Internal read-only domain access stays explicit through {@link #getReadOnlyHandler()}, separate from the null-external rejection.</p>
 */
public class ItemHandlerManager extends CapabilityHandlerManager<IInventorySlotHolder, IInventorySlot, Storage<ItemVariant>, IMekanismInventory> {

    @Nullable
    private ProxyItemHandler readOnlyDomainHandler;

    public ItemHandlerManager(@Nullable IInventorySlotHolder holder, @NotNull IMekanismInventory baseHandler) {
        super(holder, baseHandler, ItemStorage.SIDED, FabricItemLookup::expose, IInventorySlotHolder::getInventorySlots);
    }

    /**
     * Lazily gets and caches the internal, read-only domain view (official null-side semantics: reads live through the base handler, all writes and transfers
     * denied). This is Mekanism-internal access, deliberately separate from the external null-side rejection, and never exposes an external Storage.
     *
     * @implNote The cached view is revoked and dropped by {@link #invalidate} on the null side and by {@link #invalidateAll}; the mutable official
     *         {@link #getInternal()} access and the raw base handler stay live for fresh views and rollback.
     */
    public ISidedItemHandler getReadOnlyHandler() {
        if (readOnlyDomainHandler == null) {
            readOnlyDomainHandler = new ProxyItemHandler(getInternal(), null, holder);
        }
        return readOnlyDomainHandler;
    }

    @Override
    public void invalidate(BlockApiLookup<?, @Nullable Direction> capability, @Nullable Direction side) {
        super.invalidate(capability, side);
        if (side == null && capability == ItemStorage.SIDED) {
            //The internal read-only domain view shares the null-side owner lifetime, not any single physical side
            revokeReadOnlyDomainHandler();
        }
    }

    @Override
    public void invalidateAll() {
        super.invalidateAll();
        revokeReadOnlyDomainHandler();
    }

    private void revokeReadOnlyDomainHandler() {
        if (readOnlyDomainHandler != null) {
            readOnlyDomainHandler.revoke();
            readOnlyDomainHandler = null;
        }
    }

    @Override
    protected void revokeHandler(Storage<ItemVariant> handler) {
        if (handler instanceof FabricItemStorage storage) {
            //Owner obligation from the FabricItemLookup seam: permanently fail close the stable side view when its cache entry is dropped
            storage.invalidate();
        }
    }
}
