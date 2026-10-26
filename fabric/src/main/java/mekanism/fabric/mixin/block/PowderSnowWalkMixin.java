package mekanism.fabric.mixin.block;

import mekanism.common.item.interfaces.IPowderSnowWalk;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla only walks on powder snow with leather boots; ask alternative footwear through the Mekanism hook. */
@Mixin(PowderSnowBlock.class)
abstract class PowderSnowWalkMixin {

    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void mekanism$walkOnSnow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living) {
            ItemStack feet = living.getItemBySlot(EquipmentSlot.FEET);
            if (feet.getItem() instanceof IPowderSnowWalk walk && walk.canWalkOnPowderedSnow(feet, living)) {
                cir.setReturnValue(true);
            }
        }
    }
}
