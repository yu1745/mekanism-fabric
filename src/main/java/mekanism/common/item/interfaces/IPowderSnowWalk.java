package mekanism.common.item.interfaces;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric port delta for the NeoForge {@code IItemExtension#canWalkOnPowderedSnow} hook. Vanilla only recognises leather
 * boots; the powder-snow mixin asks this interface for any other footwear.
 */
public interface IPowderSnowWalk {

    boolean canWalkOnPowderedSnow(ItemStack stack, LivingEntity wearer);
}
