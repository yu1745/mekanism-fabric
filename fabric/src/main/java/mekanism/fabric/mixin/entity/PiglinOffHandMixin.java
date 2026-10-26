package mekanism.fabric.mixin.entity;

import mekanism.common.item.interfaces.IPiglinCurrencyItem;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The off-hand barter hold path checks the currency item directly in vanilla. */
@Mixin(Piglin.class)
abstract class PiglinOffHandMixin {

    @Redirect(method = "holdInOffHand", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"), require = 1)
    private boolean mekanism$currency(ItemStack stack, Item item) {
        return stack.is(item) || (stack.getItem() instanceof IPiglinCurrencyItem currency && currency.isPiglinCurrency(stack));
    }
}
