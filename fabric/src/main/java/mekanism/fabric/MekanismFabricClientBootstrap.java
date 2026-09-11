package mekanism.fabric;

import mekanism.fabric.network.FabricMekanismClientNetworking;
import mekanism.fabric.network.MekanismNetworkProtocol;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only entrypoint kept separate from the dedicated-server-safe common bootstrap. */
public final class MekanismFabricClientBootstrap implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricClient");

    @Override
    public void onInitializeClient() {
        FabricMekanismClientNetworking.initialize();
        LOGGER.info("MEKANISM_FABRIC_NETWORK_CLIENT_PROOF protocol={} receiver=true", MekanismNetworkProtocol.CURRENT);
    }
}
