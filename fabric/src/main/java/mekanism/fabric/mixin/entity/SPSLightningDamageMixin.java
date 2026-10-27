package mekanism.fabric.mixin.entity;

import mekanism.common.content.sps.SPSMultiblockData.ExperimentLightning;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Matches the official zero-damage SPS bolt without changing normal lightning strikes. */
@Mixin(Entity.class)
abstract class SPSLightningDamageMixin {
    @Redirect(method = "thunderHit", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean mekanism$experimentDamage(Entity target, DamageSource source, float damage, ServerLevel level, LightningBolt bolt) {
        return target.hurt(source, bolt instanceof ExperimentLightning ? 0 : damage);
    }
}
