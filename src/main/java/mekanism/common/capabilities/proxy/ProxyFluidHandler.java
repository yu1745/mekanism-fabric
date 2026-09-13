package mekanism.common.capabilities.proxy;

import mekanism.api.Action;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.fluid.FluidStack;
import mekanism.api.fluid.IExtendedFluidHandler;
import mekanism.api.fluid.ISidedFluidHandler;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port: implements the admitted platform-neutral {@link IExtendedFluidHandler} surface (the former NeoForge {@code IFluidHandler} parent is dropped with
 * its {@code fill}/{@code drain} bridges). All unsided methods keep delegating to the wrapped sided handler using the proxy's bound side and read-only gating.
 */
@NothingNullByDefault
public class ProxyFluidHandler extends ProxyHandler implements IExtendedFluidHandler {

    private final ISidedFluidHandler fluidHandler;

    public ProxyFluidHandler(ISidedFluidHandler fluidHandler, @Nullable Direction side, @Nullable IHolder holder) {
        super(side, holder);
        this.fluidHandler = fluidHandler;
    }

    public ISidedFluidHandler getInternalHandler() {
        return fluidHandler;
    }

    @Override
    public int getTanks() {
        return fluidHandler.getTanks(side);
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return fluidHandler.getFluidInTank(tank, side);
    }

    @Override
    public void setFluidInTank(int tank, FluidStack stack) {
        if (!readOnly) {
            fluidHandler.setFluidInTank(tank, stack, side);
        }
    }

    @Override
    public int getTankCapacity(int tank) {
        return fluidHandler.getTankCapacity(tank, side);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return !readOnly || fluidHandler.isFluidValid(tank, stack, side);
    }

    @Override
    public FluidStack insertFluid(int tank, FluidStack stack, Action action) {
        return readOnlyInsert() ? stack : fluidHandler.insertFluid(tank, stack, side, action);
    }

    @Override
    public FluidStack extractFluid(int tank, int amount, Action action) {
        return readOnlyExtract() ? FluidStack.EMPTY : fluidHandler.extractFluid(tank, amount, side, action);
    }

    @Override
    public FluidStack insertFluid(FluidStack stack, Action action) {
        return readOnlyInsert() ? stack : fluidHandler.insertFluid(stack, side, action);
    }

    @Override
    public FluidStack extractFluid(int amount, Action action) {
        return readOnlyExtract() ? FluidStack.EMPTY : fluidHandler.extractFluid(amount, side, action);
    }

    @Override
    public FluidStack extractFluid(FluidStack stack, Action action) {
        return readOnlyExtract() ? FluidStack.EMPTY : fluidHandler.extractFluid(stack, side, action);
    }
}