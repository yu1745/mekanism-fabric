package mekanism.additions.common.config;

import java.util.Collection;
import java.util.List;
import mekanism.common.config.IMekanismConfig;
import mekanism.fabric.config.FabricMekanismConfigManager;
import mekanism.fabric.config.FabricServerConfig;

public class MekanismAdditionsConfig {
    private MekanismAdditionsConfig() {
    }

    public static final AdditionsConfig additions = new AdditionsConfig();
    public static final AdditionsClientConfig client = new AdditionsClientConfig();
    private static final FabricMekanismConfigManager CLIENT_CONFIG = FabricMekanismConfigManager.createDefault();

    public static void registerConfigs() {
        FabricServerConfig.registerModuleConfig(additions);
    }

    public static void loadClient() {
        CLIENT_CONFIG.registerAndLoad(client);
    }

    public static Collection<IMekanismConfig> getConfigs() {
        return List.of(additions, client);
    }
}
