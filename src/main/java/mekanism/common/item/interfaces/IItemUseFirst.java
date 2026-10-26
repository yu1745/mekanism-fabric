package mekanism.common.item.interfaces;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Fabric port delta for the NeoForge {@code IItemExtension#onItemUseFirst} hook. Vanilla has no per-item
 * "before block use" callback, so the platform registers Fabric's {@code UseBlockCallback} and forwards the interaction
 * to items implementing this interface at the same moment NeoForge called the hook.
 */
public interface IItemUseFirst {

    InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context);
}
