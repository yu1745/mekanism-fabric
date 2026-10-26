package mekanism.common.lib.transmitter.acceptor;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.integration.energy.EnergyCompatUtils;
import mekanism.common.integration.energy.IEnergyCompat;
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
        EnergyAcceptorInfo acceptorInfo = new EnergyAcceptorInfo();
        for (IEnergyCompat energyCompat : EnergyCompatUtils.getCompats()) {
            if (energyCompat.capabilityExists()) {
                acceptorInfo.addCapability(energyCompat, level, pos, opposite, refreshListener);
            }
        }
        return acceptorInfo;
    }

    public static class EnergyAcceptorInfo implements AcceptorInfo<IStrictEnergyHandler> {

        private record CacheInfo(IEnergyCompat energyCompat, BlockEnergyCapabilityCache cache) {
        }

        private final List<CacheInfo> capabilities = new ArrayList<>();

        EnergyAcceptorInfo() {
        }

        void addCapability(IEnergyCompat energyCompat, ServerLevel level, BlockPos pos, Direction opposite, RefreshListener refreshListener) {
            capabilities.add(new CacheInfo(energyCompat, BlockEnergyCapabilityCache.create(level, pos, opposite, refreshListener, refreshListener)));
        }

        @Nullable
        @Override
        public IStrictEnergyHandler acceptor() {
            for (CacheInfo cacheInfo : capabilities) {
                IEnergyCompat energyCompat = cacheInfo.energyCompat();
                //Validate that the energy compat is actually usable
                if (energyCompat.isUsable()) {
                    //Fabric port delta: BlockEnergyCapabilityCache already resolves and wraps the external Team Reborn storage into the
                    //Joules-domain handler, so this must not wrap the result a second time (the official NeoForge cache held the raw capability).
                    IStrictEnergyHandler handler = cacheInfo.cache().getCapability();
                    if (handler != null) {
                        return handler;
                    }
                }
            }
            return null;
        }
    }
}