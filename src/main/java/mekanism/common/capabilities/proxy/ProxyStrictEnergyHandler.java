package mekanism.common.capabilities.proxy;

import mekanism.api.Action;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.energy.ISidedStrictEnergyHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

@NothingNullByDefault
public class ProxyStrictEnergyHandler extends ProxyHandler implements IStrictEnergyHandler {

    private final ISidedStrictEnergyHandler energyHandler;

    public ProxyStrictEnergyHandler(ISidedStrictEnergyHandler energyHandler, @Nullable Direction side, @Nullable IHolder holder) {
        super(side, holder);
        this.energyHandler = energyHandler;
    }

    @Override
    public int getEnergyContainerCount() {
        return isRevoked() ? 0 : energyHandler.getEnergyContainerCount(side);
    }

    @Override
    public long getEnergy(int container) {
        return isRevoked() ? 0 : energyHandler.getEnergy(container, side);
    }

    @Override
    public void setEnergy(int container, long energy) {
        if (!readOnly && !isRevoked()) {
            energyHandler.setEnergy(container, energy, side);
        }
    }

    @Override
    public long getMaxEnergy(int container) {
        return isRevoked() ? 0 : energyHandler.getMaxEnergy(container, side);
    }

    @Override
    public long getNeededEnergy(int container) {
        return isRevoked() ? 0 : energyHandler.getNeededEnergy(container, side);
    }

    @Override
    public long insertEnergy(int container, long amount, Action action) {
        return isRevoked() || readOnlyInsert() ? amount : energyHandler.insertEnergy(container, amount, side, action);
    }

    @Override
    public long extractEnergy(int container, long amount, Action action) {
        return isRevoked() || readOnlyExtract() ? 0L : energyHandler.extractEnergy(container, amount, side, action);
    }

    @Override
    public long insertEnergy(long amount, Action action) {
        return isRevoked() || readOnlyInsert() ? amount : energyHandler.insertEnergy(amount, side, action);
    }

    @Override
    public long extractEnergy(long amount, Action action) {
        return isRevoked() || readOnlyExtract() ? 0L : energyHandler.extractEnergy(amount, side, action);
    }
}