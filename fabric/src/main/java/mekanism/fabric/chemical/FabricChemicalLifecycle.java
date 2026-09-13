package mekanism.fabric.chemical;

import mekanism.fabric.config.FabricChemicalConfig;
import mekanism.fabric.datamaps.FabricChemicalDataMaps;

/** Common composition root for registry, world policy, and atomic chemical data-map epochs. */
public final class FabricChemicalLifecycle {
    private static boolean initialized;

    private FabricChemicalLifecycle() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        FabricChemicalRegistryLifecycle.initialize();
        FabricChemicalConfig.initializeCallbacks();
        FabricChemicalDataMaps.initializeCallbacks();
        initialized = true;
    }
}
