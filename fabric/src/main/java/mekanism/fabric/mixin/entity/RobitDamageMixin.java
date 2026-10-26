package mekanism.fabric.mixin.entity;

import mekanism.common.entity.EntityRobit;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The official Robit damage callback runs after mitigation and before hurt's death check. */
@Mixin(LivingEntity.class)
abstract class RobitDamageMixin {

    @ModifyArg(method = "actuallyHurt", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"), index = 0)
    private float mekanism$payRobitDamageWithEnergy(float requestedHealth) {
        if ((Object) this instanceof EntityRobit robit) {
            //The argument has not been clamped to zero: a 1 HP Robit still pays for the complete damage.
            robit.onDamageTaken(robit.getHealth() - requestedHealth);
            return robit.getMaxHealth();
        }
        return requestedHealth;
    }
}
