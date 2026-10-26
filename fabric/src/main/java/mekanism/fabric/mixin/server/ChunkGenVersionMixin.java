package mekanism.fabric.mixin.server;

import mekanism.api.SerializationConstants;
import mekanism.common.CommonWorldTickHandler;
import mekanism.common.config.MekanismConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves the official root NBT version, including missing legacy fields being version zero. */
@Mixin(ChunkSerializer.class)
abstract class ChunkGenVersionMixin {

    @Inject(method = "read", at = @At("RETURN"))
    private static void mekanism$readGenerationVersion(ServerLevel level, PoiManager poi, RegionStorageInfo info, ChunkPos pos,
          CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
        ChunkAccess chunk = cir.getReturnValue();
        if (chunk instanceof ImposterProtoChunk wrapper) {
            chunk = wrapper.getWrapped();
        }
        int version = tag.getInt(SerializationConstants.WORLD_GEN_VERSION);
        if (version < MekanismConfig.world.userGenVersion.get()) {
            chunk.setAttached(CommonWorldTickHandler.WORLD_GEN_VERSION, version);
        } else {
            //The official owner only retained versions pending an upgrade; current/future chunks save the configured version.
            chunk.removeAttached(CommonWorldTickHandler.WORLD_GEN_VERSION);
        }
    }

    @Inject(method = "write", at = @At("RETURN"))
    private static void mekanism$writeGenerationVersion(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
        Integer version = chunk.getAttached(CommonWorldTickHandler.WORLD_GEN_VERSION);
        cir.getReturnValue().putInt(SerializationConstants.WORLD_GEN_VERSION,
              version == null ? MekanismConfig.world.userGenVersion.get() : version);
    }
}
