package mekanism.fabric.config;

import mekanism.common.config.ChemicalConfigAccess;
import mekanism.common.config.MekanismConfig;

/** Publishes the canonical process-local common config to chemical consumers. */
public final class FabricChemicalConfig {
    private static boolean commonInitialized;

    private FabricChemicalConfig() {
    }

    public static synchronized void initializeCommon(FabricMekanismConfigManager manager) {
        if (commonInitialized) return;
        manager.registerAndLoad(MekanismConfig.common);
        ChemicalConfigAccess.publishCommon(MekanismConfig.common);
        commonInitialized = true;
    }
}
