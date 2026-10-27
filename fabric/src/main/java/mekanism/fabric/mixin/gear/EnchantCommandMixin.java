package mekanism.fabric.mixin.gear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mekanism.fabric.gear.FabricEnchantmentRules;
import net.minecraft.core.Holder;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnchantCommand.class)
abstract class EnchantCommandMixin {
    @WrapOperation(method = "enchant", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean mekanism$supportedCommand(Enchantment value, ItemStack stack, Operation<Boolean> original,
          @Local(argsOnly = true) Holder<Enchantment> enchantment) {
        return FabricEnchantmentRules.supports(stack, enchantment, original.call(value, stack));
    }
}
