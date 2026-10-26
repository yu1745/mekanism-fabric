package mekanism.fabric.mixin.tile;

import mekanism.common.tile.interfaces.IChunkUnloadListener;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla removes every block entity of an unloading chunk through {@link LevelChunk#clearAllBlockEntities()}, which is the
 * moment NeoForge exposed as {@code BlockEntity#onChunkUnloaded()}. Invoke the Mekanism listener there, before the entities
 * are removed, so transmitter shares are released exactly as the official override did.
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkUnloadMixin {

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void mekanism$chunkUnloaded(CallbackInfo ci) {
        for (BlockEntity blockEntity : ((LevelChunk) (Object) this).getBlockEntities().values()) {
            if (blockEntity instanceof IChunkUnloadListener listener) {
                listener.onChunkUnloaded();
            }
        }
    }
}
