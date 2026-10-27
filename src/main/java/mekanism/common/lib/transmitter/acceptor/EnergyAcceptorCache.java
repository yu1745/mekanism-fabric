package mekanism.common.lib.transmitter.acceptor;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.integration.energy.BlockEnergyCapabilityCache;
import mekanism.common.lib.transmitter.acceptor.EnergyAcceptorCache.EnergyAcceptorInfo;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

@NothingNullByDefault
public class EnergyAcceptorCache extends AbstractAcceptorCache<IStrictEnergyHandler, EnergyAcceptorInfo> {

    public EnergyAcceptorCache(TileEntityTransmitter transmitterTile) {
        super(transmitterTile);
    }

    @Override
    protected EnergyAcceptorInfo initializeCache(ServerLevel level, BlockPos pos, Direction opposite, RefreshListener refreshListener) {
        return new EnergyAcceptorInfo(BlockEnergyCapabilityCache.create(level, pos, opposite, refreshListener, refreshListener));
    }

    public static class EnergyAcceptorInfo implements AcceptorInfo<IStrictEnergyHandler> {

        private final BlockEnergyCapabilityCache cache;

        EnergyAcceptorInfo(BlockEnergyCapabilityCache cache) {
            this.cache = cache;
        }

        @Nullable
        @Override
        public IStrictEnergyHandler acceptor() {
            //The cache resolves native strict energy first and consults enabled external integrations only as fallback.
            return cache.getCapability();
        }
    }
}