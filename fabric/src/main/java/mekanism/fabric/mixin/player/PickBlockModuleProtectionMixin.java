package mekanism.fabric.mixin.player;

import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.item.gear.ItemMekaTool;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve the official preference not to replace a Meka item with installed modules when picking a block. */
@Mixin(Inventory.class)
abstract class PickBlockModuleProtectionMixin {

    @Redirect(method = "getSuitableHotbarSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEnchanted()Z"))
    private boolean mekanism$protectInstalledModules(ItemStack stack) {
        if (stack.isEnchanted()) {
            return true;
        }
        if (stack.getItem() instanceof ItemMekaTool tool) {
            return tool.hasInstalledModules(stack);
        }
        if (stack.getItem() instanceof ItemMekaSuitArmor armor) {
            return armor.hasInstalledModules(stack);
        }
        return false;
    }
}
