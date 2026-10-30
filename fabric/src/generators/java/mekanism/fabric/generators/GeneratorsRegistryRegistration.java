package mekanism.fabric.generators;

import mekanism.api.MekanismIMC;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.fabric.gear.FabricModuleRegistry;
import mekanism.generators.common.registries.GeneratorsChemicals;
import mekanism.generators.common.registries.GeneratorsModules;

/** Registers the original addon declarations in their construction-thread owners before freeze. */
public final class GeneratorsRegistryRegistration {
    private GeneratorsRegistryRegistration() {
    }

    public static void chemicals() {
        GeneratorsChemicals.CHEMICALS.registerAll(FabricChemicalRegistryLifecycle.allocate());
    }

    public static void modules() {
        GeneratorsModules.MODULES.registerAll(FabricModuleRegistry.allocate());
    }

    public static void support() {
        MekanismIMC.addMekaSuitHelmetModules(GeneratorsModules.SOLAR_RECHARGING_UNIT);
        MekanismIMC.addMekaSuitPantsModules(GeneratorsModules.GEOTHERMAL_GENERATOR_UNIT);
    }
}
