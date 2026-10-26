package mekanism.common.item.interfaces;

import net.minecraft.world.item.ItemStack;

/**
 * Fabric port delta for the NeoForge {@code IItemExtension#isPiglinCurrency} hook, consulted by the barter mixins.
 */
public interface IPiglinCurrencyItem {

    boolean isPiglinCurrency(ItemStack stack);
}
