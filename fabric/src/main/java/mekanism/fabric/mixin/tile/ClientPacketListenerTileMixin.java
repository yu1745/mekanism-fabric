package mekanism.fabric.mixin.tile;

import mekanism.common.tile.base.TileEntityUpdateable;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Client-only reduced-tag dispatch for the vanilla {@link ClientboundBlockEntityDataPacket} path.
 *
 * <p>NeoForge patches this consumer to {@code BlockEntity#onDataPacket} (which for Mekanism then applies the reduced tag). Vanilla would instead run the full
 * {@code loadWithComponents} reader. This hook replaces exactly that one invocation for {@link TileEntityUpdateable} only and dispatches through the retained
 * {@code onDataPacket} entry point, so subclass overrides and the empty-tag contract are preserved. Every other block entity keeps vanilla's original call.</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerTileMixin {

    @Redirect(method = "method_38542", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"),
          require = 1)
    private void mekanism$vanillaBlockEntityPacketReducedUpdate(BlockEntity blockEntity, CompoundTag tag, HolderLookup.Provider registries,
          ClientboundBlockEntityDataPacket packet) {
        if (blockEntity instanceof TileEntityUpdateable updateable) {
            //The vanilla packet path stays on onDataPacket, exactly like the NeoForge patch: an empty tag then performs no reduced load
            updateable.onDataPacket(((ClientPacketListener) (Object) this).getConnection(), packet, registries);
        } else {
            blockEntity.loadWithComponents(tag, registries);
        }
    }
}
