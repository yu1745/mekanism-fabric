package mekanism.fabric.mixin.gear;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Predicate;
import mekanism.fabric.gear.FabricEnchantmentRules;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EnchantmentHelper.class)
abstract class EnchantmentSelectionMixin {
    @ModifyArg(method = "getAvailableEnchantmentResults", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"))
    private static Predicate<Holder<Enchantment>> mekanism$primaryItems(Predicate<Holder<Enchantment>> original, @Local(argsOnly = true) ItemStack stack) {
        return enchantment -> FabricEnchantmentRules.primary(stack, enchantment, original.test(enchantment));
    }
}
