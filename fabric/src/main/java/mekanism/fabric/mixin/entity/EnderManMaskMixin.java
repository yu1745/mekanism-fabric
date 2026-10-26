package mekanism.fabric.mixin.entity;

import mekanism.common.item.interfaces.IEnderMaskItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla only exempts carved pumpkins; ask the worn helmet through the Mekanism replacement of {@code isEnderMask}. */
@Mixin(EnderMan.class)
abstract class EnderManMaskMixin {

    @Inject(method = "isLookingAtMe", at = @At("HEAD"), cancellable = true)
    private void mekanism$enderMask(Player player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof IEnderMaskItem mask && mask.isEnderMask(head, player, (EnderMan) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
