package mekanism.fabric.mixin.gear;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import mekanism.common.content.gear.ModuleContainer;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.item.gear.ItemMekaTool;
import mekanism.common.registries.MekanismDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restores Mekanism module enchantments in vanilla gameplay queries without modifying stored item components. */
@Mixin(EnchantmentHelper.class)
abstract class GameplayEnchantmentsMixin {

    @Inject(method = "getItemEnchantmentLevel", at = @At("RETURN"), cancellable = true)
    private static void mekanism$moduleEnchantmentLevel(Holder<Enchantment> enchantment, ItemStack stack,
          CallbackInfoReturnable<Integer> result) {
        ModuleContainer modules = mekanism$modules(stack);
        if (modules != null) {
            result.setReturnValue(Math.max(result.getReturnValueI(), modules.moduleBasedEnchantments().getLevel(enchantment)));
        }
    }

    @Redirect(method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object mekanism$itemEnchantments(ItemStack stack, DataComponentType<ItemEnchantments> type, Object fallback) {
        return mekanism$merge(stack, stack.getOrDefault(type, (ItemEnchantments) fallback));
    }

    @Redirect(method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private static Object mekanism$equippedEnchantments(ItemStack stack, DataComponentType<ItemEnchantments> type) {
        return mekanism$merge(stack, stack.get(type));
    }

    private static ModuleContainer mekanism$modules(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemMekaTool || stack.getItem() instanceof ItemMekaSuitArmor)) {
            return null;
        }
        return stack.get(MekanismDataComponents.MODULE_CONTAINER.get());
    }

    private static ItemEnchantments mekanism$merge(ItemStack stack, ItemEnchantments stored) {
        ModuleContainer modules = mekanism$modules(stack);
        if (modules == null || modules.moduleBasedEnchantments().isEmpty()) {
            return stored;
        }
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(stored == null ? ItemEnchantments.EMPTY : stored);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : modules.moduleBasedEnchantments().entrySet()) {
            merged.upgrade(entry.getKey(), entry.getIntValue());
        }
        return merged.toImmutable();
    }
}
