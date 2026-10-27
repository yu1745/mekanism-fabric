package mekanism.fabric.client.render.item;

import mekanism.api.gear.IModuleHelper;
import mekanism.api.gear.IModule;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registries.MekanismModules;
import mekanism.common.content.gear.shared.ModuleColorModulationUnit;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The official mekasuit item color: the second (tint) layer takes the color-modulation module's tint, and renders
 * untinted (-1) without the module.
 */
public final class FabricMekaSuitItemColor {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricMekaSuit");

    private FabricMekaSuitItemColor() {
    }

    public static void register() {
        ColorProviderRegistry.ITEM.register((stack, index) -> {
            if (index == 1) {
                IModule<ModuleColorModulationUnit> colorModulationUnit = IModuleHelper.INSTANCE.getModule(stack, MekanismModules.COLOR_MODULATION_UNIT);
                if (colorModulationUnit == null) {
                    return -1;
                }
                return colorModulationUnit.getCustomInstance().tintARGB();
            }
            return -1;
        }, MekanismItems.MEKASUIT_HELMET.asItem(), MekanismItems.MEKASUIT_BODYARMOR.asItem(), MekanismItems.MEKASUIT_PANTS.asItem(),
              MekanismItems.MEKASUIT_BOOTS.asItem());
        LOGGER.info("MEKANISM_FABRIC_MEKASUIT_ITEM_COLOR_PROOF registered=4");
    }
}
