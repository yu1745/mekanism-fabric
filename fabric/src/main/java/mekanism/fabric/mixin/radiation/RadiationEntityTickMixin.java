package mekanism.fabric.mixin.radiation;

import mekanism.common.lib.radiation.PlayerExposure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors the original EntityTickEvent.Post site, not baseTick or passenger ticking. */
@Mixin(ServerLevel.class)
abstract class RadiationEntityTickMixin {
    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
    private void mekanism$afterEntityTick(Entity entity, CallbackInfo ci) {
        PlayerExposure.onLivingTick(entity);
    }
}
