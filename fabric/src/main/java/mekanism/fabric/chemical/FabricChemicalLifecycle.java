package mekanism.fabric.chemical;

import mekanism.fabric.datamaps.FabricChemicalDataMaps;

/** Common composition root for registry, world policy, and atomic chemical data-map epochs. */
public final class FabricChemicalLifecycle {
    private static boolean initialized;

    private FabricChemicalLifecycle() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        FabricChemicalRegistryLifecycle.initialize();
        FabricChemicalDataMaps.initializeCallbacks();
        initialized = true;
    }
}
