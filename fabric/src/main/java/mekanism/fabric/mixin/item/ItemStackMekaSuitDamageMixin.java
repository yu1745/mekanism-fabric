package mekanism.fabric.mixin.item;

import mekanism.common.item.gear.ItemMekaSuitArmor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * NeoForge's {@code damageItem} hook returns zero for MekaSuit. Apply the same amount at the shared vanilla server
 * entry point while preserving vanilla's remaining zero-damage handling.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMekaSuitDamageMixin {

    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
          at = @At("HEAD"), argsOnly = true, index = 1)
    private int mekanism$noSuitDamage(int amount) {
        return ((ItemStack) (Object) this).getItem() instanceof ItemMekaSuitArmor ? 0 : amount;
    }
}
