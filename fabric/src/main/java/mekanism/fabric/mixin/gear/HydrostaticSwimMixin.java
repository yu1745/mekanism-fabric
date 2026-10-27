package mekanism.fabric.mixin.gear;

import com.llamalad7.mixinextras.sugar.Local;
import mekanism.common.content.gear.mekasuit.ModuleHydrostaticRepulsorUnit;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Mekanism's +1 swim-speed modifier, applied where NeoForge uses it for water movement. */
@Mixin(LivingEntity.class)
abstract class HydrostaticSwimMixin {
    private boolean mekanism$boost() {
        return (Object) this instanceof Player player && ModuleHydrostaticRepulsorUnit.hasActiveSwimBoost(player);
    }

    @ModifyArg(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V", ordinal = 0), index = 0)
    private float mekanism$swimAcceleration(float acceleration) {
        return mekanism$boost() ? acceleration * 2 : acceleration;
    }

    @ModifyConstant(method = "jumpInLiquid", constant = @Constant(doubleValue = (double) 0.04F))
    private double mekanism$swimUp(double amount, @Local(argsOnly = true) TagKey<Fluid> fluid) {
        return FluidTags.WATER.equals(fluid) && mekanism$boost() ? amount * 2 : amount;
    }

    @ModifyConstant(method = "goDownInWater", constant = @Constant(doubleValue = (double) -0.04F))
    private double mekanism$swimDown(double amount) {
        return mekanism$boost() ? amount * 2 : amount;
    }
}
