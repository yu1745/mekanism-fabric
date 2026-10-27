package mekanism.fabric.mixin.client;

import mekanism.common.item.block.ItemBlockTooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ItemBlockTooltip.class, remap = false)
public interface ItemBlockTooltipAccessor {
    @Invoker("exposesEnergyCapOrTooltips")
    boolean mekanism$exposesEnergyCapOrTooltips();
}
