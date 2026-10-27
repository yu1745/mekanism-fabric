package mekanism.fabric.gear;

import mekanism.common.item.gear.ItemAtomicDisassembler;
import mekanism.common.item.gear.ItemElectricBow;
import mekanism.common.item.gear.ItemFlamethrower;
import mekanism.common.item.gear.ItemHazmatSuitArmor;
import mekanism.common.item.gear.ItemMekaTool;
import mekanism.common.item.gear.ItemScubaMask;
import mekanism.common.item.gear.ItemSpecialArmor;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** Mekanism's item-specific NeoForge enchantment decisions at their Fabric call sites. */
public final class FabricEnchantmentRules {
    private FabricEnchantmentRules() {
    }

    private static boolean gatesOnEnchantability(Item item) {
        return item instanceof ItemAtomicDisassembler || item instanceof ItemMekaTool || item instanceof ItemFlamethrower
              || item instanceof ItemHazmatSuitArmor || item instanceof ItemSpecialArmor;
    }

    public static boolean supports(ItemStack stack, Holder<Enchantment> enchantment, boolean vanilla) {
        Item item = stack.getItem();
        if (item instanceof ItemScubaMask && enchantment.is(Enchantments.AQUA_AFFINITY)) {
            return true;
        }
        return vanilla && (!gatesOnEnchantability(item) || item.isEnchantable(stack));
    }

    public static boolean primary(ItemStack stack, Holder<Enchantment> enchantment, boolean vanilla) {
        Item item = stack.getItem();
        if (item instanceof ItemScubaMask && enchantment.is(Enchantments.AQUA_AFFINITY)) {
            return true;
        }
        if (item instanceof ItemElectricBow && enchantment.is(Enchantments.FLAME)) {
            return false;
        }
        return vanilla && (!gatesOnEnchantability(item) || item.isEnchantable(stack));
    }

    public static boolean acceptsBook(ItemStack stack) {
        Item item = stack.getItem();
        return !gatesOnEnchantability(item) || item.isEnchantable(stack);
    }
}
