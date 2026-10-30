package mekanism.tools.client;

import mekanism.tools.common.config.MekanismToolsConfig;
import net.fabricmc.api.ClientModInitializer;

public class ToolsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MekanismToolsConfig.loadClient();
        ToolsClientRegistration.initialize();
    }
}
