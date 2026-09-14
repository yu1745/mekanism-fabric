package mekanism.common.lib.transmitter.acceptor;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.util.WorldUtils;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Fresh per-pipe fluid acceptor cache for MechanicalPipe (Task052 continuation08 ownership boundary).
 *
 * <p>Unlike {@link AcceptorCache}, the {@link AcceptorInfo} stores only lookup context (level, neighbor position,
 * opposite face) and never the returned Fabric {@link Storage}. Each {@link #acceptor()} resolution performs a fresh
 * {@code FluidStorage.SIDED.find} on the physical opposite face, so a provider that was formerly {@code null} is
 * rediscovered on the next resolution without any invalidation callback. Unloaded neighbors are skipped without
 * loading chunks; {@code null} means there is no fluid target on that side.
 *
 * <p>Because no {@code BlockCapabilityCache} is retained, the refresh listener is not consumed here; connection
 * bitmap rediscovery relies on the official {@code refreshAcceptorConnections} / neighbor-update policy that callers
 * run before the acceptor decision, per the continuation08 contract.
 */
@NothingNullByDefault
public class FluidAcceptorCache extends AbstractAcceptorCache<Storage<FluidVariant>, FluidAcceptorCache.FreshLookupInfo> {

    public FluidAcceptorCache(TileEntityTransmitter transmitterTile) {
        super(transmitterTile);
    }

    @Override
    protected FreshLookupInfo initializeCache(ServerLevel level, BlockPos pos, Direction opposite, RefreshListener refreshListener) {
        //The refresh listener is intentionally not retained: there is no provider-side cache to invalidate.
        return new FreshLookupInfo(level, pos, opposite);
    }

    /**
     * Lookup context only. Never stores the returned {@code Storage}; every {@link #acceptor()} call performs a fresh
     * operation-local lookup, and results are never reused next tick.
     */
    static final class FreshLookupInfo implements AcceptorInfo<Storage<FluidVariant>> {

        private final ServerLevel level;
        private final BlockPos pos;
        private final Direction opposite;

        FreshLookupInfo(ServerLevel level, BlockPos pos, Direction opposite) {
            this.level = level;
            this.pos = pos;
            this.opposite = opposite;
        }

        @Nullable
        @Override
        public Storage<FluidVariant> acceptor() {
            //Server level captured at cache initialization; skip unloaded neighbors without loading chunks
            if (level.isRemoved() || !WorldUtils.isChunkLoaded(level, pos)) {
                return null;
            }
            return FluidStorage.SIDED.find(level, pos, opposite);
        }
    }
}
