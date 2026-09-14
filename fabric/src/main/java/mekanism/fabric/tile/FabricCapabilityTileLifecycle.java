package mekanism.fabric.tile;

import mekanism.common.tile.base.CapabilityTileEntity;
import mekanism.common.tile.machine.TileEntityOredictionificator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;

/**
 * Mekanism-specific server lifecycle wiring for the capability base tile.
 *
 * <p>Fabric's pinned lookup implementation caches only the block entity and its provider, never the returned API, and its invalidation is a lazy field reset with
 * no listener callbacks. This helper therefore tracks the real server block-entity lifecycle directly: load opens a fresh capability exposure generation, unload
 * closes it and revokes every retained view. Block-entity removal also reaches {@link CapabilityTileEntity#setRemoved()}, while unload never calls
 * {@code setRemoved()} on the server, so {@code BLOCK_ENTITY_UNLOAD} is the real unload hook. The pinned Fabric lifecycle implementation already forwards that
 * event to every block entity of a chunk on chunk unload (including the portal edge case) and to every block entity of its tracked chunks on world unload, so
 * these two events alone cover the whole server lifecycle without a direct chunk callback.</p>
 *
 * <p>These callbacks only consume real lifecycle events; they never synthesize world events and never load or create chunks.</p>
 */
public final class FabricCapabilityTileLifecycle {

    private FabricCapabilityTileLifecycle() {
    }

    public static void initialize() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof CapabilityTileEntity tile) {
                //A block entity actually entering a server world opens a fresh exposure generation; component data may not be loaded yet, which the guard never reads
                tile.openCapabilityExposure();
            }
            if (blockEntity instanceof TileEntityOredictionificator tile) {
                tile.onLoad();
            }
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof CapabilityTileEntity tile) {
                tile.closeCapabilityExposure();
            }
            if (blockEntity instanceof TileEntityOredictionificator tile) {
                tile.onUnload();
            }
        });
    }
}
