package mekanism.fabric.mixin.entity;

import mekanism.common.CommonPlayerTickHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric port delta: the NeoForge post-player-tick event is replaced by the tail of {@code Player.tick} (also reached from
 * {@code ServerPlayer.doTick}), the same anchor the radiation player-tick mixin uses.
 */
@Mixin(Player.class)
abstract class PlayerGearTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void mekanism$afterPlayerTick(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            CommonPlayerTickHandler.onPlayerTick(player);
        }
    }
}
