package mekanism.fabric.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;

/** Physical-client-only registration for the configuration-phase handshake. */
public final class FabricMekanismClientNetworking {
    private static boolean initialized;

    private FabricMekanismClientNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        if (!ClientConfigurationNetworking.registerGlobalReceiver(
              FabricMekanismNetworking.REQUEST_TYPE, FabricMekanismClientNetworking::handleRequest)) {
            throw new IllegalStateException("A client receiver is already registered for " + FabricMekanismNetworking.REQUEST_ID);
        }
        initialized = true;
    }

    private static void handleRequest(FabricMekanismNetworking.VersionRequest request, ClientConfigurationNetworking.Context context) {
        MekanismNetworkProtocol.Compatibility compatibility = MekanismNetworkProtocol.checkRemote(request.protocolVersion());
        context.responseSender().sendPacket(new FabricMekanismNetworking.VersionResponse(
              MekanismNetworkProtocol.CURRENT, compatibility.compatible()
        ));
    }
}
