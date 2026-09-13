package mekanism.fabric.mixin.tile;

import mekanism.common.tile.base.TileEntityUpdateable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Client-only reduced-tag dispatch for the initial chunk packet.
 *
 * <p>NeoForge patches the initial chunk-tag consumer to call {@code BlockEntity#handleUpdateTag}; vanilla replays the tag through the full
 * {@code BlockEntity#loadWithComponents}, which would run a subclass's full reader and corrupt every save-only field the reduced tag omits. This hook replaces exactly
 * that one invocation for {@link TileEntityUpdateable} only: every other block entity keeps vanilla's original call, and server-side saved-NBT loading is untouched.
 * The enclosing lambda is the only place in {@code LevelChunk} that performs this call, so the redirect is unambiguous.</p>
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Redirect(method = "method_31716", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"),
          require = 1)
    private void mekanism$reducedInitialChunkTag(BlockEntity blockEntity, CompoundTag tag, HolderLookup.Provider registries) {
        if (blockEntity instanceof TileEntityUpdateable updateable) {
            //A real reduced load: the base handler calls the non-virtual super reader and the vanilla components codec only
            updateable.handleUpdateTag(tag, registries);
        } else {
            blockEntity.loadWithComponents(tag, registries);
        }
    }
}
