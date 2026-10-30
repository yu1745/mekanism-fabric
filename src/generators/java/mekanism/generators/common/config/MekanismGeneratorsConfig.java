package mekanism.generators.common.config;

import java.util.Collection;
import java.util.List;
import mekanism.common.config.IMekanismConfig;
import mekanism.fabric.config.FabricServerConfig;

public class MekanismGeneratorsConfig {
    private MekanismGeneratorsConfig() {
    }

    public static final GeneratorsConfig generators = new GeneratorsConfig();
    public static final GeneratorsGearConfig gear = new GeneratorsGearConfig();
    public static final GeneratorsStorageConfig storageConfig = new GeneratorsStorageConfig();

    public static void registerConfigs() {
        getConfigs().forEach(FabricServerConfig::registerModuleConfig);
    }

    public static Collection<IMekanismConfig> getConfigs() {
        return List.of(generators, gear, storageConfig);
    }
}
