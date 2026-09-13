package mekanism.fabric.mixin.tile;

import mekanism.common.tile.base.TileEntityUpdateable;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only reduced-tag dispatch for the vanilla {@link ClientboundBlockEntityDataPacket} path.
 *
 * <p>NeoForge patches this consumer to {@code BlockEntity#onDataPacket} (which for Mekanism then applies the reduced tag). Vanilla would instead run the full
 * {@code loadWithComponents} reader. Dispatch before vanilla's empty-tag guard: NeoForge invokes {@code onDataPacket} even for empty tags, leaving the decision to
 * the tile override. Only Mekanism tiles are handled here; every other block entity keeps the complete vanilla lambda.</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerTileMixin {

    @Inject(method = "method_38542", at = @At("HEAD"), cancellable = true, require = 1)
    private void mekanism$vanillaBlockEntityPacketReducedUpdate(ClientboundBlockEntityDataPacket packet, BlockEntity blockEntity, CallbackInfo ci) {
        if (blockEntity instanceof TileEntityUpdateable updateable) {
            ClientPacketListener listener = (ClientPacketListener) (Object) this;
            updateable.onDataPacket(listener.getConnection(), packet, listener.registryAccess());
            ci.cancel();
        }
    }
}
