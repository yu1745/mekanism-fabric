package mekanism.fabric.mixin.gear;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Predicate;
import mekanism.fabric.gear.FabricEnchantmentRules;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EnchantRandomlyFunction.class)
abstract class EnchantRandomlyMixin {
    @Shadow @Final private boolean onlyCompatible;

    @ModifyArg(method = "run", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"))
    private Predicate<Holder<Enchantment>> mekanism$supportedLoot(Predicate<Holder<Enchantment>> original, @Local(argsOnly = true) ItemStack stack) {
        if (!onlyCompatible) return original;
        return enchantment -> FabricEnchantmentRules.supports(stack, enchantment, original.test(enchantment));
    }
}
