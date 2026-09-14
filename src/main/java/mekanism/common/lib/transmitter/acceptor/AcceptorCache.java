package mekanism.common.lib.transmitter.acceptor;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.common.lib.transmitter.acceptor.AcceptorCache.CacheBasedInfo;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.util.WorldUtils;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiCache;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Chemical/heat lookup context. Fabric caches the neighboring block entity, not the returned domain handler;
 * every resolution checks the real transmitter owner and re-runs the provider on the physical opposite face.
 * Callers refresh connection bits before use, including formerly null PULL/heat sides.
 */
@NothingNullByDefault
public class AcceptorCache<ACCEPTOR> extends AbstractAcceptorCache<ACCEPTOR, CacheBasedInfo<ACCEPTOR>> {

    private final TileEntityTransmitter transmitterTile;
    private final BlockApiLookup<ACCEPTOR, Direction> capability;

    public AcceptorCache(TileEntityTransmitter transmitterTile, BlockApiLookup<ACCEPTOR, Direction> capability) {
        super(transmitterTile);
        this.transmitterTile = transmitterTile;
        this.capability = capability;
    }

    @Override
    protected CacheBasedInfo<ACCEPTOR> initializeCache(ServerLevel level, BlockPos pos, Direction opposite, RefreshListener refreshListener) {
        //There is no native invalidation callback; network snapshots and direct pull/heat consumers refresh instead.
        return new CacheBasedInfo<>(transmitterTile, BlockApiCache.create(capability, level, pos), opposite);
    }

    public record CacheBasedInfo<ACCEPTOR>(TileEntityTransmitter transmitterTile, BlockApiCache<ACCEPTOR, Direction> cache,
                                          Direction opposite) implements AcceptorInfo<ACCEPTOR> {

        @Nullable
        @Override
        public ACCEPTOR acceptor() {
            if (transmitterTile.isRemoved() || !transmitterTile.isLoaded() || transmitterTile.getLevel() != cache.getWorld()
                  || !WorldUtils.isChunkLoaded(cache.getWorld(), cache.getPos())) {
                return null;
            }
            return cache.find(opposite);
        }
    }
}
