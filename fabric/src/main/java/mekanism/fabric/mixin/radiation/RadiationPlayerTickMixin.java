package mekanism.fabric.mixin.radiation;

import mekanism.common.lib.radiation.PlayerExposure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PlayerTickEvent.Post was at Player.tick return (also reached from ServerPlayer.doTick), not ServerPlayer.tick. */
@Mixin(Player.class)
abstract class RadiationPlayerTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mekanism$afterPlayerTick(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            PlayerExposure.tickServer(player);
        }
    }
}
