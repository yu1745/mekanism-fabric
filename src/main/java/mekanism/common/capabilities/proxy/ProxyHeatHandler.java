package mekanism.common.capabilities.proxy;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.heat.ISidedHeatHandler;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

@NothingNullByDefault
public class ProxyHeatHandler extends ProxyHandler implements IHeatHandler {

    private final ISidedHeatHandler heatHandler;

    public ProxyHeatHandler(ISidedHeatHandler heatHandler, @Nullable Direction side, @Nullable IHolder holder) {
        super(side, holder);
        this.heatHandler = heatHandler;
    }

    @Override
    public int getHeatCapacitorCount() {
        return isRevoked() ? 0 : heatHandler.getHeatCapacitorCount(side);
    }

    @Override
    public double getTemperature(int capacitor) {
        return isRevoked() ? 0 : heatHandler.getTemperature(capacitor, side);
    }

    @Override
    public double getInverseConduction(int capacitor) {
        return isRevoked() ? HeatAPI.DEFAULT_INVERSE_CONDUCTION : heatHandler.getInverseConduction(capacitor, side);
    }

    @Override
    public double getHeatCapacity(int capacitor) {
        return isRevoked() ? 0 : heatHandler.getHeatCapacity(capacitor, side);
    }

    @Override
    public void handleHeat(int capacitor, double transfer) {
        if (!readOnly && !isRevoked()) {
            heatHandler.handleHeat(capacitor, transfer, side);
        }
    }

    @Override
    public double getTotalTemperature() {
        return isRevoked() ? 0 : heatHandler.getTotalTemperature(side);
    }

    @Override
    public double getTotalInverseConduction() {
        return isRevoked() ? HeatAPI.DEFAULT_INVERSE_CONDUCTION : heatHandler.getTotalInverseConductionCoefficient(side);
    }

    @Override
    public double getTotalHeatCapacity() {
        return isRevoked() ? 0 : heatHandler.getTotalHeatCapacity(side);
    }

    @Override
    public void handleHeat(double transfer) {
        if (!readOnly && !isRevoked()) {
            heatHandler.handleHeat(transfer, side);
        }
    }
}
