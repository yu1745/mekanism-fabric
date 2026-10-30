package mekanism.tools.common.registries;

import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registration.impl.CreativeTabDeferredRegister;
import mekanism.tools.common.MekanismTools;
import mekanism.tools.common.ToolsLang;
import mekanism.tools.common.item.ItemMekanismArmor;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;

public class ToolsCreativeTabs {

    public static final CreativeTabDeferredRegister CREATIVE_TABS = new CreativeTabDeferredRegister(MekanismTools.MODID, ToolsCreativeTabs::addToExistingTabs);

    public static final MekanismDeferredHolder<CreativeModeTab, CreativeModeTab> TOOLS = CREATIVE_TABS.registerMain(ToolsLang.MEKANISM_TOOLS, ToolsItems.DIAMOND_PAXEL, builder ->
          builder.backgroundTexture(MekanismTools.rl("textures/gui/creative_tab.png"))
                .displayItems((displayParameters, output) -> CreativeTabDeferredRegister.addToDisplay(ToolsItems.ITEMS, output))
    );

    private static void addToExistingTabs(ResourceKey<CreativeModeTab> tabKey, CreativeModeTab.Output output, CreativeModeTab.ItemDisplayParameters parameters) {
        if (tabKey == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for (Holder<Item> holder : ToolsItems.ITEMS.getEntries()) {
                if (holder.value() instanceof DiggerItem) {
                    CreativeTabDeferredRegister.addToDisplay(output, holder);
                }
            }
        } else if (tabKey == CreativeModeTabs.COMBAT) {
            for (Holder<Item> itemProvider : ToolsItems.ITEMS.getEntries()) {
                Item item = itemProvider.value();
                if (item instanceof ItemMekanismArmor || item instanceof SwordItem || item instanceof ShieldItem) {
                    CreativeTabDeferredRegister.addToDisplay(output, itemProvider);
                }
            }
        }
    }
}