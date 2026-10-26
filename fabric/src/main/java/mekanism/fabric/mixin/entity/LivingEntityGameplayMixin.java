package mekanism.fabric.mixin.entity;

import mekanism.common.CommonPlayerTickHandler;
import mekanism.common.CommonPlayerTickHandler.DamageReduction;
import mekanism.common.CommonPlayerTickHandler.FallResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric port delta for the NeoForge living-entity events:
 *
 * <ul>
 * <li>{@code LivingIncomingDamageEvent} → {@code hurt} cancellation plus an amount modifier. Both use
 * {@link CommonPlayerTickHandler#reduceIncomingDamage} whose per-call cache makes the armor energy extraction happen exactly once
 * regardless of which injector runs first.</li>
 * <li>{@code EntityInvulnerabilityCheckEvent} → {@code isInvulnerableTo} (the entity's own override calls super, so the check
 * still runs for players that are not already invulnerable).</li>
 * <li>{@code LivingDeathEvent} → {@code die} head.</li>
 * <li>{@code LivingFallEvent} → {@code causeFallDamage} cancellation plus a distance modifier with the same one-shot cache.</li>
 * <li>{@code LivingJumpEvent} → {@code jumpFromGround} tail.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityGameplayMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void mekanism$cancelAbsorbedDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        DamageReduction reduction = CommonPlayerTickHandler.reduceIncomingDamage((LivingEntity) (Object) this, source, amount);
        if (reduction.cancel()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, index = 2)
    private float mekanism$reduceIncomingDamage(float value, DamageSource source, float amount) {
        //Handler signature is (current value, then all target arguments); the value is the incoming amount.
        DamageReduction reduction = CommonPlayerTickHandler.reduceIncomingDamage((LivingEntity) (Object) this, source, value);
        return reduction.cancel() ? value : reduction.remaining();
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void mekanism$clearDamageReduction(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        CommonPlayerTickHandler.clearIncomingDamage();
    }

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void mekanism$radiationImmunity(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (CommonPlayerTickHandler.checkRadiationImmunity((LivingEntity) (Object) this, source)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void mekanism$flamethrowerDeath(DamageSource source, CallbackInfo ci) {
        CommonPlayerTickHandler.onLivingDeath((LivingEntity) (Object) this, source);
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void mekanism$cancelAbsorbedFall(float fallDistance, float damageMultiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        FallResult result = CommonPlayerTickHandler.reduceFallDamage((LivingEntity) (Object) this, fallDistance, damageMultiplier, source);
        if (result.cancel()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "causeFallDamage", at = @At("HEAD"), argsOnly = true, index = 1)
    private float mekanism$reduceFallDistance(float value, float fallDistance, float damageMultiplier, DamageSource source) {
        //Handler signature is (current value, then all target arguments); the value is the fall distance.
        FallResult result = CommonPlayerTickHandler.reduceFallDamage((LivingEntity) (Object) this, value, damageMultiplier, source);
        return result.cancel() ? value : result.remainingDistance();
    }

    @Inject(method = "causeFallDamage", at = @At("RETURN"))
    private void mekanism$clearFallReduction(float fallDistance, float damageMultiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        CommonPlayerTickHandler.clearFallDamage();
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void mekanism$onJump(CallbackInfo ci) {
        CommonPlayerTickHandler.onLivingJump((LivingEntity) (Object) this);
    }
}
