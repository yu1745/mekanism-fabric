package mekanism.tools.common.config;

import java.util.Collection;
import java.util.List;
import mekanism.common.config.IMekanismConfig;
import mekanism.fabric.config.FabricMekanismConfigManager;
import mekanism.fabric.config.FabricServerConfig;

public class MekanismToolsConfig {
    private MekanismToolsConfig() {
    }

    //Materials are initialized before the spawn policy that references their registry prefixes.
    public static final ToolsMaterialConfig materials = new ToolsMaterialConfig();
    public static final ToolsConfig tools = new ToolsConfig();
    public static final ToolsClientConfig toolsClient = new ToolsClientConfig();
    private static final FabricMekanismConfigManager LOCAL_CONFIGS = FabricMekanismConfigManager.createDefault();

    public static void registerConfigs() {
        LOCAL_CONFIGS.registerAndLoad(materials);
        FabricServerConfig.registerModuleConfig(tools);
    }

    public static void loadClient() {
        LOCAL_CONFIGS.registerAndLoad(toolsClient);
    }

    public static Collection<IMekanismConfig> getConfigs() {
        return List.of(materials, tools, toolsClient);
    }
}
