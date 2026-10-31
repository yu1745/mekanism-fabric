package mekanism.tools.common;

import mekanism.api.MekanismAPI;
import mekanism.common.base.IModModule;
import mekanism.common.base.ModModuleState;
import mekanism.common.lib.Version;
import mekanism.tools.common.config.MekanismToolsConfig;
import mekanism.tools.common.registries.ToolsArmorMaterials;
import mekanism.tools.common.registries.ToolsCreativeTabs;
import mekanism.tools.common.registries.ToolsItems;
import mekanism.tools.common.registries.ToolsRecipeSerializers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;

public class MekanismTools implements IModModule, ModInitializer {
    public static final String MODID = "mekanismtools";
    public static MekanismTools instance;
    public final Version versionNumber;

    public MekanismTools() {
        instance = this;
        versionNumber = Version.fromFabricVersion(FabricLoader.getInstance().getModContainer(MODID).orElseThrow().getMetadata().getVersion().getFriendlyString());
    }

    @Override
    public void onInitialize() {
        ModModuleState.addModule(this);
        MekanismToolsConfig.registerConfigs();
        ToolsArmorMaterials.ARMOR_MATERIALS.registerAll(BuiltInRegistries.ARMOR_MATERIAL);
        ToolsItems.ITEMS.registerAll(BuiltInRegistries.ITEM);
        ToolsRecipeSerializers.RECIPE_SERIALIZERS.registerAll(BuiltInRegistries.RECIPE_SERIALIZER);
        ToolsCreativeTabs.CREATIVE_TABS.registerAll(BuiltInRegistries.CREATIVE_MODE_TAB);
        for (var tab : java.util.List.of(CreativeModeTabs.TOOLS_AND_UTILITIES, CreativeModeTabs.COMBAT)) {
            ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> ToolsCreativeTabs.CREATIVE_TABS.getAddToExistingTabs()
                  .addToDisplay(tab, entries, entries.getContext()));
        }
        MekanismAPI.logger.info("Loaded 'Mekanism: Tools' module.");
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    @Override public Version getVersion() { return versionNumber; }
    @Override public String getName() { return "Tools"; }
}
