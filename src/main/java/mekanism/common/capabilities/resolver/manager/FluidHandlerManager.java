package mekanism.common.capabilities.resolver.manager;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.fabric.fluid.FabricFluidLookup;
import mekanism.fabric.fluid.FabricFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class to make reading instead of having as messy generics
 *
 * <p>Fabric port: the exposed type is the transaction {@link Storage} view created through the {@link FabricFluidLookup} seam, not a Mekanism domain handler. The
 * base handler surface is narrowed from the official {@code ISidedFluidHandler} to {@link IMekanismFluidHandler}, which the only current consumer
 * ({@code TileEntityMekanism}) implements; the null side is internal and deliberately resolves to no external Storage, matching the accepted adapters.</p>
 */
public class FluidHandlerManager extends CapabilityHandlerManager<IFluidTankHolder, IExtendedFluidTank, Storage<FluidVariant>, IMekanismFluidHandler> {

    public FluidHandlerManager(@Nullable IFluidTankHolder holder, @NotNull IMekanismFluidHandler baseHandler) {
        super(holder, baseHandler, FluidStorage.SIDED, FabricFluidLookup::expose, IFluidTankHolder::getTanks);
    }

    @Override
    protected void revokeHandler(Storage<FluidVariant> handler) {
        if (handler instanceof FabricFluidStorage storage) {
            //Owner obligation from the FabricFluidLookup seam: permanently fail close the stable side view when its cache entry is dropped
            storage.invalidate();
        }
    }
}
