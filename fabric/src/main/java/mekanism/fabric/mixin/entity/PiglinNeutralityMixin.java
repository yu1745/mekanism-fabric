package mekanism.fabric.mixin.entity;

import mekanism.common.item.interfaces.IPiglinNeutralItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla checks gold armor through {@code isWearingGold}; NeoForge also asked {@code makesPiglinsNeutral}. */
@Mixin(PiglinAi.class)
abstract class PiglinNeutralityMixin {

    @Inject(method = "isWearingGold", at = @At("HEAD"), cancellable = true)
    private static void mekanism$neutralArmor(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        for (ItemStack stack : entity.getArmorAndBodyArmorSlots()) {
            if (stack.getItem() instanceof IPiglinNeutralItem neutral && neutral.makesPiglinsNeutral(stack, entity)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
