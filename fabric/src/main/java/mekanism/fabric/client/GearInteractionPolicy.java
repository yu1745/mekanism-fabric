package mekanism.fabric.client;

import mekanism.common.item.ItemEnergized;
import mekanism.common.item.ItemPortableQIODashboard;
import mekanism.common.item.block.ItemBlockTooltip;
import mekanism.common.item.gear.ItemElectricBow;
import mekanism.common.item.gear.ItemFlamethrower;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.fabric.mixin.client.ItemBlockTooltipAccessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The original Mekanism item-specific rules for component-only re-equip and block-break updates. */
public final class GearInteractionPolicy {
    private GearInteractionPolicy() {
    }

    public static boolean ignoresReequipComponents(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ItemEnergized || item instanceof ItemElectricBow || item instanceof ItemFlamethrower
              || item instanceof ItemMekaSuitArmor || item instanceof ItemPortableQIODashboard || isEnergyBlockItem(item);
    }

    private static boolean isEnergyBlockItem(Item item) {
        return item instanceof ItemBlockTooltip<?> && ((ItemBlockTooltipAccessor) item).mekanism$exposesEnergyCapOrTooltips();
    }
}
