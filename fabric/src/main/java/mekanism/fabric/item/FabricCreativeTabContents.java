package mekanism.fabric.item;

import java.util.List;
import mekanism.common.registration.impl.CreativeTabDeferredRegister.ExistingTabContents;
import mekanism.common.registries.MekanismCreativeTabs;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * Fabric replacement for the NeoForge {@code BuildCreativeModeTabContentsEvent} listener: register the official
 * existing-tab content callback once per vanilla tab, preserving the tab key and the real display parameters.
 */
public final class FabricCreativeTabContents {

    private static final List<ResourceKey<CreativeModeTab>> TARGET_TABS = List.of(
          CreativeModeTabs.BUILDING_BLOCKS, CreativeModeTabs.NATURAL_BLOCKS, CreativeModeTabs.FUNCTIONAL_BLOCKS,
          CreativeModeTabs.REDSTONE_BLOCKS, CreativeModeTabs.TOOLS_AND_UTILITIES, CreativeModeTabs.COMBAT,
          CreativeModeTabs.FOOD_AND_DRINKS, CreativeModeTabs.INGREDIENTS);

    private FabricCreativeTabContents() {
    }

    public static void initialize() {
        ExistingTabContents contents = MekanismCreativeTabs.CREATIVE_TABS.getAddToExistingTabs();
        for (ResourceKey<CreativeModeTab> tabKey : TARGET_TABS) {
            ItemGroupEvents.modifyEntriesEvent(tabKey).register(entries -> contents.addToDisplay(tabKey, entries, entries.getContext()));
        }
    }
}
