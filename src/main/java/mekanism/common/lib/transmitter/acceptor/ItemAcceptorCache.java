package mekanism.common.lib.transmitter.acceptor;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.util.WorldUtils;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Fresh per-transmitter item acceptor cache for {@code LogisticalTransporterBase} (Task052 continuation09).
 *
 * <p>Reuses the reviewed design (not the sources) of the fluid caches: the {@link AcceptorInfo} stores only lookup
 * context (owning transmitter tile, initialized server level, neighbor position, opposite face) and never the returned
 * Fabric {@link Storage}. Each {@link FreshLookupInfo#acceptor()} resolution performs a fresh
 * {@code ItemStorage.SIDED.find} on the physical opposite face, so a provider that was formerly {@code null} is
 * rediscovered on the next resolution without any invalidation callback. Unloaded neighbors are skipped without
 * loading chunks; {@code null} means there is no item target on that side. No external Storage alias is persisted.
 */
@NothingNullByDefault
public class ItemAcceptorCache extends AbstractAcceptorCache<Storage<ItemVariant>, ItemAcceptorCache.FreshLookupInfo> {

    private final TileEntityTransmitter transmitterTile;

    public ItemAcceptorCache(TileEntityTransmitter transmitterTile) {
        super(transmitterTile);
        this.transmitterTile = transmitterTile;
    }

    @Override
    protected FreshLookupInfo initializeCache(ServerLevel level, BlockPos pos, Direction opposite, RefreshListener refreshListener) {
        //The refresh listener is intentionally not retained: there is no provider-side cache to invalidate.
        //The owning transmitter tile is bound as lookup context: removal/loading state and current level identity
        // are validated against it on every resolution.
        return new FreshLookupInfo(transmitterTile, level, pos, opposite);
    }

    /**
     * Lookup context only. Binds the owning transmitter tile plus the server level captured at cache initialization;
     * never stores the returned {@link Storage}. Every {@link #acceptor()} call validates the tile's removal/loading
     * state, that the tile still lives in the same server level that initialized the cache, and that the neighbor
     * chunk is loaded, then performs a fresh operation-local {@code ItemStorage.SIDED.find}. Results are never reused
     * next tick.
     */
    static final class FreshLookupInfo implements AcceptorInfo<Storage<ItemVariant>> {

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
        public Storage<ItemVariant> acceptor() {
            if (transmitterTile.isRemoved() || !transmitterTile.isLoaded()) {
                return null;
            }
            if (transmitterTile.getLevel() != initializedLevel) {
                return null;
            }
            if (!WorldUtils.isChunkLoaded(initializedLevel, transmitterTile.getBlockPos()) ||
                  initializedLevel.getBlockEntity(transmitterTile.getBlockPos()) != transmitterTile ||
                  !WorldUtils.isChunkLoaded(initializedLevel, pos)) {
                return null;
            }
            return ItemStorage.SIDED.find(initializedLevel, pos, opposite);
        }
    }
}
