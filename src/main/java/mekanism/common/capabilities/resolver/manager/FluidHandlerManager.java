package mekanism.common.capabilities.resolver.manager;

import mekanism.api.fluid.IExtendedFluidHandler;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.proxy.ProxyFluidHandler;
import mekanism.fabric.fluid.FabricFluidLookup;
import mekanism.fabric.fluid.FabricFluidStorage;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class to make reading instead of having as messy generics
 *
 * <p>Fabric port: the exposed type is the transaction {@link Storage} view created through the {@link FabricFluidLookup} seam, not a Mekanism domain handler. The
 * base handler surface is narrowed from the official {@code ISidedFluidHandler} to {@link IMekanismFluidHandler} (itself an {@code IExtendedFluidHandler} via
 * {@code ISidedFluidHandler}), which the audited deferred consumers ({@code TileEntityMekanism} and {@code TileEntityMechanicalPipe} with its
 * {@code DynamicFluidHandler}) implement; the null side is internal and deliberately resolves to no external Storage, matching the accepted adapters. Internal
 * read-only domain access stays explicit through {@link #getReadOnlyHandler()}, separate from the null-external rejection.</p>
 */
public class FluidHandlerManager extends CapabilityHandlerManager<IFluidTankHolder, IExtendedFluidTank, Storage<FluidVariant>, IMekanismFluidHandler> {

    @Nullable
    private ProxyFluidHandler readOnlyDomainHandler;

    public FluidHandlerManager(@Nullable IFluidTankHolder holder, @NotNull IMekanismFluidHandler baseHandler) {
        super(holder, baseHandler, FluidStorage.SIDED, FabricFluidLookup::expose, IFluidTankHolder::getTanks);
    }

    /**
     * Lazily gets and caches the internal, read-only domain view (official null-side semantics: reads live through the base handler, all writes and transfers
     * denied). This is Mekanism-internal access, deliberately separate from the external null-side rejection, and never exposes an external Storage.
     *
     * @implNote The cached view is revoked and dropped by {@link #invalidate} on the null side and by {@link #invalidateAll}; the mutable official
     *         {@link #getInternal()} access and the raw base handler stay live for fresh views and rollback.
     */
    public IExtendedFluidHandler getReadOnlyHandler() {
        if (readOnlyDomainHandler == null) {
            readOnlyDomainHandler = new ProxyFluidHandler(getInternal(), null, holder);
        }
        return readOnlyDomainHandler;
    }

    @Override
    public void invalidate(BlockApiLookup<?, @Nullable Direction> capability, @Nullable Direction side) {
        super.invalidate(capability, side);
        if (side == null && capability == FluidStorage.SIDED) {
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
    protected void revokeHandler(Storage<FluidVariant> handler) {
        if (handler instanceof FabricFluidStorage storage) {
            //Owner obligation from the FabricFluidLookup seam: permanently fail close the stable side view when its cache entry is dropped
            storage.invalidate();
        }
    }
}
