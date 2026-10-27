package mekanism.fabric.mixin.gear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mekanism.fabric.gear.FabricEnchantmentRules;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilMenu.class)
abstract class AnvilEnchantmentMixin {
    @WrapOperation(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mekanism$supportsEnchantment(Enchantment value, ItemStack stack, Operation<Boolean> original,
          @Local Holder<Enchantment> enchantment) {
        return FabricEnchantmentRules.supports(stack, enchantment, original.call(value, stack));
    }

    @WrapOperation(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V"))
    private void mekanism$bookEligibility(ResultContainer results, int slot, ItemStack result, Operation<Void> original) {
        ItemStack book = ((AnvilMenu) (Object) this).getSlot(1).getItem();
        if (!result.isEmpty() && book.has(DataComponents.STORED_ENCHANTMENTS) && !FabricEnchantmentRules.acceptsBook(result)) {
            result = ItemStack.EMPTY;
        }
        original.call(results, slot, result);
    }
}
