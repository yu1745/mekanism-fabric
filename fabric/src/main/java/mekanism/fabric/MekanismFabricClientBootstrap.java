package mekanism.fabric;

import mekanism.fabric.chemical.FabricChemicalClientLifecycle;
import mekanism.fabric.config.FabricClientConfigLifecycle;
import mekanism.fabric.network.FabricChemicalClientNetworking;
import mekanism.fabric.network.FabricMekanismClientNetworking;
import mekanism.fabric.network.MekanismNetworkProtocol;
import mekanism.fabric.recipe.FabricRecipeCacheClientLifecycle;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only entrypoint kept separate from the dedicated-server-safe common bootstrap. */
public final class MekanismFabricClientBootstrap implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricClient");

    @Override
    public void onInitializeClient() {
        FabricClientConfigLifecycle.initialize();
        FabricChemicalClientLifecycle.initialize();
        FabricRecipeCacheClientLifecycle.initialize();
        FabricMekanismClientNetworking.initialize();
        FabricChemicalClientNetworking.initialize();
        LOGGER.info("MEKANISM_FABRIC_NETWORK_CLIENT_PROOF protocol={} receiver=true recipe_mixin_client_only=true", MekanismNetworkProtocol.CURRENT);
    }
}
