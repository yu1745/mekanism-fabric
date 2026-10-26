package mekanism.common.item.interfaces;

import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric port delta for the NeoForge {@code IItemExtension#isEnderMask} hook. Vanilla only exempts carved pumpkins; the
 * enderman mixin asks the worn helmet for the same decision.
 */
public interface IEnderMaskItem {

    boolean isEnderMask(ItemStack stack, Player player, EnderMan enderman);
}
