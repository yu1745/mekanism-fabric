package mekanism.fabric.tile;

import mekanism.common.tile.base.CapabilityTileEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Mekanism-specific server lifecycle wiring for the capability base tile.
 *
 * <p>Fabric's pinned lookup implementation caches only the block entity and its provider, never the returned API, and its invalidation is a lazy field reset with
 * no listener callbacks. This helper therefore tracks the real server block-entity lifecycle directly: load opens a fresh capability exposure generation, unload
 * closes it and revokes every retained view. Block-entity removal also reaches {@link CapabilityTileEntity#setRemoved()}, but chunk unload (and world unload)
 * never calls {@code setRemoved()} on the server and fires no per-block-entity unload event in the pinned Fabric version, so the chunk-unload event closes every
 * capability tile still held by that chunk.</p>
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
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof CapabilityTileEntity tile) {
                tile.closeCapabilityExposure();
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof CapabilityTileEntity tile) {
                    tile.closeCapabilityExposure();
                }
            }
        });
    }
}
