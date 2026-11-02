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
 * Fresh per-pipe fluid acceptor cache for MechanicalPipe.
 *
 * <p>Unlike {@link AcceptorCache}, the {@link AcceptorInfo} stores only lookup context (owning transmitter tile,
 * initialized server level, neighbor position, opposite face) and never the returned Fabric {@link Storage}. Each
 * {@link FreshLookupInfo#acceptor()} resolution performs a fresh
 * {@code FluidStorage.SIDED.find} on the physical opposite face, so a provider that was formerly {@code null} is
 * rediscovered on the next resolution without any invalidation callback. Unloaded neighbors are skipped without
 * loading chunks; {@code null} means there is no fluid target on that side.
 *
 * <p>Because no {@code BlockCapabilityCache} is retained, the refresh listener is not consumed here; connection
 * bitmap rediscovery relies on the official {@code refreshAcceptorConnections} / neighbor-update policy that callers
 * run before the acceptor decision,.
 */
@NothingNullByDefault
public class FluidAcceptorCache extends AbstractAcceptorCache<Storage<FluidVariant>, FluidAcceptorCache.FreshLookupInfo> {

    private final TileEntityTransmitter transmitterTile;

    public FluidAcceptorCache(TileEntityTransmitter transmitterTile) {
        super(transmitterTile);
        this.transmitterTile = transmitterTile;
    }

    @Override
    protected FreshLookupInfo initializeCache(ServerLevel level, BlockPos pos, Direction opposite, RefreshListener refreshListener) {
        //The refresh listener is intentionally not retained: there is no provider-side cache to invalidate.
        //The owning transmitter tile is bound as lookup context: removal/loading state and current level identity
        // are validated against it on every resolution (the same owner Transmitter.isValid() uses).
        return new FreshLookupInfo(transmitterTile, level, pos, opposite);
    }

    /**
     * Lookup context only. Binds the owning transmitter tile plus the server level captured at cache initialization;
     * never stores the returned {@link Storage}. Every {@link #acceptor()} call validates the tile's removal/loading
     * state, that the tile still lives in the same server level that initialized the cache, and that the neighbor
     * chunk is loaded, then performs a fresh operation-local {@code FluidStorage.SIDED.find}. Results are never
     * reused next tick; no level lifecycle API is invented and no global unload hook is registered.
     */
    static final class FreshLookupInfo implements AcceptorInfo<Storage<FluidVariant>> {

        private final TileEntityTransmitter transmitterTile;
        private final ServerLevel initializedLevel;
        private final BlockPos pos;
        private final Direction opposite;

        FreshLookupInfo(TileEntityTransmitter transmitterTile, ServerLevel initializedLevel, BlockPos pos, Direction opposite) {
            this.transmitterTile = transmitterTile;
            this.initializedLevel = initializedLevel;
            this.pos = pos;
            this.opposite = opposite;
        }

        @Nullable
        @Override
        public Storage<FluidVariant> acceptor() {
            //The transmitter tile is the real removal/loading owner (Transmitter.isValid() uses !isRemoved && isLoaded):
            // skip the lookup once it is removed or its chunk is unloaded
            if (transmitterTile.isRemoved() || !transmitterTile.isLoaded()) {
                return null;
            }
            //The cache is only valid for the level it was initialized in; a tile that ended up in a different level
            // (or whose level is not currently set) must not be answered from this stale context
            if (transmitterTile.getLevel() != initializedLevel) {
                return null;
            }
            //Skip unloaded neighbors without loading chunks
            if (!WorldUtils.isChunkLoaded(initializedLevel, pos)) {
                return null;
            }
            return FluidStorage.SIDED.find(initializedLevel, pos, opposite);
        }
    }
}
