package mekanism.common.config;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import mekanism.fabric.config.FabricMekanismConfigManager;

public class MekanismConfig {

    private MekanismConfig() {
    }

    private static final Set<IMekanismConfig> KNOWN_CONFIGS = new java.util.LinkedHashSet<>();
    public static final ClientConfig client = new ClientConfig();
    public static final CommonConfig common = new CommonConfig();
    public static final GeneralConfig general = new GeneralConfig();
    public static final GearConfig gear = new GearConfig();
    public static final MekanismStartupConfig startup = new MekanismStartupConfig();
    public static final StorageConfig storage = new StorageConfig();
    public static final TierConfig tiers = new TierConfig();
    public static final UsageConfig usage = new UsageConfig();
    public static final WorldConfig world = new WorldConfig();

    /**
     * Creates and registers every common/server-side Mekanism config through the real Fabric config manager. Fabric port delta: replaces the NeoForge
     * {@code ModContainer.registerConfig} wiring; the client config is registered separately on the physical client by the client config lifecycle.
     */
    public static void registerConfigs(FabricMekanismConfigManager manager) {
        MekanismConfigHelper.registerConfig(manager, KNOWN_CONFIGS, common);
        MekanismConfigHelper.registerConfig(manager, KNOWN_CONFIGS, general);
        MekanismConfigHelper.registerConfig(manager, KNOWN_CONFIGS, gear);
        MekanismConfigHelper.registerConfig(manager, KNOWN_CONFIGS, startup);
        MekanismConfigHelper.registerConfig(manager, KNOWN_CONFIGS, storage);
        MekanismConfigHelper.registerConfig(manager, KNOWN_CONFIGS, tiers);
        MekanismConfigHelper.registerConfig(manager, KNOWN_CONFIGS, usage);
        MekanismConfigHelper.registerConfig(manager, KNOWN_CONFIGS, world);
    }

    /** Registers the client-only config through the client-owned Fabric config manager. */
    public static void registerClientConfig(FabricMekanismConfigManager manager) {
        MekanismConfigHelper.registerConfig(manager, KNOWN_CONFIGS, client);
    }

    public static Collection<IMekanismConfig> getConfigs() {
        return Collections.unmodifiableSet(KNOWN_CONFIGS);
    }
}
