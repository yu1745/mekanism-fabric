package mekanism.fabric.mixin.transmitter;

import mekanism.common.lib.transmitter.TransmitterNetworkRegistry;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Original chunk-ticket event site, after native holder scheduling and before the end-tick domain queue is consumed. */
@Mixin(ChunkMap.class)
abstract class TransmitterTicketLevelMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "updateChunkScheduling", at = @At("RETURN"))
    private void mekanism$ticketLevelChanged(long chunkPos, int newLevel, ChunkHolder holder, int oldLevel,
          CallbackInfoReturnable<ChunkHolder> cir) {
        //The original platform did not fire on the native early return when both ticket levels are unloaded.
        if (ChunkLevel.isLoaded(oldLevel) || ChunkLevel.isLoaded(newLevel)) {
            mekanism.common.command.ChunkCommand.onTicketLevelChange(level, chunkPos, oldLevel, newLevel);
            TransmitterNetworkRegistry.onTicketLevelChange(level, chunkPos, oldLevel, newLevel);
        }
    }
}
