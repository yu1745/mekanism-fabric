package mekanism.fabric.mixin.server;

import mekanism.common.lib.chunkloading.ChunkLoaderTickets;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Restores the official forceTicks gate: (naturalSpawningAllowed && playerNearby) || ownedForceTicks. */
@Mixin(ServerChunkCache.class)
abstract class ChunkLoaderTickMixin {

    @Shadow @Final private ServerLevel level;

    @Redirect(method = "tickChunks", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerLevel;isNaturalSpawningAllowed(Lnet/minecraft/world/level/ChunkPos;)Z"))
    private boolean mekanism$allowForcedChunk(ServerLevel world, ChunkPos pos) {
        return world.isNaturalSpawningAllowed(pos) || ChunkLoaderTickets.shouldForceTicks(world, pos);
    }

    @Redirect(method = "tickChunks", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ChunkMap;anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;)Z"))
    private boolean mekanism$allowRemoteForcedChunk(ChunkMap chunkMap, ChunkPos pos) {
        return chunkMap.anyPlayerCloseEnoughForSpawning(pos) || ChunkLoaderTickets.shouldForceTicks(level, pos);
    }
}
