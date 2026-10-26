package mekanism.fabric.mixin.entity;

import mekanism.common.item.interfaces.IPiglinCurrencyItem;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Barter currency: NeoForge asked {@code stack.isPiglinCurrency()} instead of the hardcoded gold ingot. */
@Mixin(PiglinAi.class)
abstract class PiglinAiCurrencyMixin {

    @Inject(method = "isBarterCurrency", at = @At("HEAD"), cancellable = true)
    private static void mekanism$currency(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof IPiglinCurrencyItem currency && currency.isPiglinCurrency(stack)) {
            cir.setReturnValue(true);
        }
    }
}
