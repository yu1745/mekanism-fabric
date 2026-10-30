package mekanism.generators.client;

import net.fabricmc.api.ClientModInitializer;

public class GeneratorsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GeneratorsClientRegistration.init();
        GeneratorsClientRegistration.registerRenderers();
        GeneratorsClientRegistration.registerLayer();
        GeneratorsClientRegistration.registerClientReloadListeners();
        GeneratorsClientRegistration.registerScreens();
        GeneratorsClientRegistration.registerItemColorHandlers();
        GeneratorsClientRegistration.registerClientExtensions();
    }
}
