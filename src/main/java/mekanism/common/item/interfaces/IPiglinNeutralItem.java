package mekanism.common.item.interfaces;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric port delta for the NeoForge {@code IItemExtension#makesPiglinsNeutral} hook, queried by the piglin neutrality
 * mixin in the same place the vanilla gold-armor check runs.
 */
public interface IPiglinNeutralItem {

    boolean makesPiglinsNeutral(ItemStack stack, LivingEntity wearer);
}
