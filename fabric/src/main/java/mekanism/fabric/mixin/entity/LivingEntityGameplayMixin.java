package mekanism.fabric.mixin.entity;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
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
 * <li>{@code LivingIncomingDamageEvent} → {@code hurt} cancellation plus an amount modifier. Both run after vanilla rejection guards; the amount modifier runs after the cancellation check. They use
 * {@link CommonPlayerTickHandler#reduceIncomingDamage} once, sharing its result in this invocation's local variable.
 * Recursive damage and cancelled calls cannot retain or reuse another invocation's paid absorption.</li>
 * <li>{@code EntityInvulnerabilityCheckEvent} → {@code isInvulnerableTo} (the entity's own override calls super, so the check
 * still runs for players that are not already invulnerable).</li>
 * <li>{@code LivingDeathEvent} → {@code die} head.</li>
 * <li>{@code LivingFallEvent} → {@code causeFallDamage} cancellation plus a distance modifier sharing the same invocation-local result.</li>
 * <li>{@code LivingJumpEvent} → {@code jumpFromGround} tail.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityGameplayMixin {

    @Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"), cancellable = true)
    private void mekanism$cancelAbsorbedDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir,
          @Share("incomingDamage") LocalRef<DamageReduction> result) {
        DamageReduction reduction = CommonPlayerTickHandler.reduceIncomingDamage((LivingEntity) (Object) this, source, amount);
        result.set(reduction);
        if (reduction.cancel()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "hurt", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;noActionTime:I", opcode = org.objectweb.asm.Opcodes.PUTFIELD), argsOnly = true, index = 2)
    private float mekanism$reduceIncomingDamage(float value, @Share("incomingDamage") LocalRef<DamageReduction> result) {
        return result.get().remaining();
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
    private void mekanism$cancelAbsorbedFall(float fallDistance, float damageMultiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir,
          @Share("fallReduction") LocalRef<FallResult> shared) {
        FallResult result = CommonPlayerTickHandler.reduceFallDamage((LivingEntity) (Object) this, fallDistance, damageMultiplier, source);
        shared.set(result);
        if (result.cancel()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "causeFallDamage", at = @At(value = "LOAD", ordinal = 0), argsOnly = true, index = 1)
    private float mekanism$reduceFallDistance(float value, @Share("fallReduction") LocalRef<FallResult> result) {
        return result.get().remainingDistance();
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void mekanism$onJump(CallbackInfo ci) {
        CommonPlayerTickHandler.onLivingJump((LivingEntity) (Object) this);
    }
}
