package mekanism.fabric.mixin.item;

import mekanism.common.item.gear.ItemMekaSuitArmor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's {@code damageItem} hook let the MekaSuit refuse all durability loss. Vanilla has no item damage hook, so the
 * damage entry point itself is short-circuited for MekaSuit pieces.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMekaSuitDamageMixin {

    @Inject(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
          at = @At("HEAD"), cancellable = true)
    private void mekanism$noSuitDamage(int amount, LivingEntity entity, EquipmentSlot slot, CallbackInfo ci) {
        if (((ItemStack) (Object) this).getItem() instanceof ItemMekaSuitArmor) {
            ci.cancel();
        }
    }
}
