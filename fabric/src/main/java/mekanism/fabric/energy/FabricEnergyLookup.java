package mekanism.fabric.energy;

import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.holder.IHolder;
import mekanism.common.capabilities.proxy.ProxyStrictEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

/**
 * The sole lookup and exposure seam for the Fabric energy boundary.
 */
public final class FabricEnergyLookup {

    private FabricEnergyLookup() {
    }

    /**
     * Finds external energy at one physical block side. Null/internal-side access is deliberately never granted.
     */
    @Nullable
    public static IStrictEnergyHandler find(@Nullable Level level, @Nullable BlockPos pos, @Nullable Direction side,
          @Nullable IEnergyConversion conversion) {
        if (level == null || pos == null || side == null || !FabricEnergyConversions.isUsable(conversion)) {
            return null;
        }
        return wrap(EnergyStorage.SIDED.find(level, pos, side), side, conversion);
    }

    /**
     * Direct seam used after a lookup and by local tests; it performs no registry mutation.
     */
    @Nullable
    static IStrictEnergyHandler wrap(@Nullable EnergyStorage storage, @Nullable Direction side,
          @Nullable IEnergyConversion conversion) {
        if (storage == null || side == null || !FabricEnergyConversions.isUsable(conversion)) {
            return null;
        }
        if (storage instanceof FabricEnergyStorage fabricStorage && fabricStorage.hasConversion(conversion)) {
            return fabricStorage.stableHandler();
        }
        return new FabricStrictEnergyHandler(storage, conversion);
    }

    /**
     * Creates one stable external view for a physical side. The owner is responsible for caching that view for the
     * lifetime of the stable side-bound handler and invalidating it with the owner lifecycle.
     */
    @Nullable
    public static EnergyStorage expose(@Nullable IMekanismStrictEnergyHandler handler, @Nullable Direction side,
          @Nullable IHolder holder, @Nullable IEnergyConversion conversion) {
        if (handler == null || side == null || !FabricEnergyConversions.isUsable(conversion)
              || !handler.canHandleEnergy() || handler.getEnergyContainerCount(side) <= 0) {
            return null;
        }
        return new FabricEnergyStorage(new ProxyStrictEnergyHandler(handler, side, holder), conversion);
    }
}
