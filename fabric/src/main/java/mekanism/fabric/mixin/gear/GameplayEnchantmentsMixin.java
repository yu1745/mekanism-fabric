package mekanism.fabric.mixin.gear;

import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import mekanism.api.gear.IModuleContainer;
import mekanism.api.gear.IModuleHelper;
import mekanism.common.item.gear.ItemElectricBow;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.item.gear.ItemMekaTool;
import mekanism.fabric.gear.FabricEnchantmentLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
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
        IModuleContainer modules = mekanism$modules(stack);
        if (modules != null) {
            result.setReturnValue(Math.max(result.getReturnValueI(), modules.moduleBasedEnchantments().getLevel(enchantment)));
        } else if (mekanism$fireMode(stack) && enchantment.is(Enchantments.FLAME)) {
            result.setReturnValue(Math.max(result.getReturnValueI(), 1));
        }
    }

    @Redirect(method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object mekanism$itemEnchantments(ItemStack stack, DataComponentType<ItemEnchantments> type, Object fallback) {
        Holder<Enchantment> flame = mekanism$fireMode(stack) ? FabricEnchantmentLookup.currentFlame() : null;
        return mekanism$merge(stack, stack.getOrDefault(type, (ItemEnchantments) fallback), flame);
    }

    @Redirect(method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private static Object mekanism$equippedEnchantments(ItemStack stack, DataComponentType<ItemEnchantments> type,
          @Local(argsOnly = true) LivingEntity entity) {
        Holder<Enchantment> flame = mekanism$fireMode(stack) ? FabricEnchantmentLookup.flame(entity.registryAccess()) : null;
        return mekanism$merge(stack, stack.get(type), flame);
    }

    private static IModuleContainer mekanism$modules(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemMekaTool || stack.getItem() instanceof ItemMekaSuitArmor)) {
            return null;
        }
        return IModuleHelper.INSTANCE.getModuleContainer(stack);
    }

    private static boolean mekanism$fireMode(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemElectricBow bow && bow.getMode(stack);
    }

    private static ItemEnchantments mekanism$merge(ItemStack stack, ItemEnchantments stored, Holder<Enchantment> flame) {
        IModuleContainer modules = mekanism$modules(stack);
        boolean hasModules = modules != null && !modules.moduleBasedEnchantments().isEmpty();
        if (!hasModules && flame == null) {
            return stored;
        }
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(stored == null ? ItemEnchantments.EMPTY : stored);
        if (hasModules) {
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : modules.moduleBasedEnchantments().entrySet()) {
                merged.upgrade(entry.getKey(), entry.getIntValue());
            }
        }
        if (flame != null) merged.upgrade(flame, 1);
        return merged.toImmutable();
    }
}
