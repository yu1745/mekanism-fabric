package mekanism.common.config;

import java.util.Collection;
import mekanism.fabric.config.FabricMekanismConfigManager;

public class MekanismConfigHelper {

    private MekanismConfigHelper() {
    }

    //Fabric port delta: the original owned the FMLPaths game-relative config directory and NeoForge config events; on Fabric the real
    // FabricMekanismConfigManager owns the Mekanism config directory, file claims, and load/unload cache invalidation.

    /**
     * Registers a config through the real Fabric config manager and tracks it so callers can enumerate every known config.
     */
    public static void registerConfig(FabricMekanismConfigManager manager, Collection<IMekanismConfig> knownConfigs, IMekanismConfig config) {
        manager.registerAndLoad(config);
        if (!knownConfigs.add(config)) {
            throw new IllegalStateException("Config is already registered: " + config.getFileName());
        }
    }
}
