package mekanism.fabric.network;

import mekanism.common.network.to_client.PacketPortalFX;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-side dispatch for teleporter particle effects. */
public final class FabricPortalClientNetworking {
    private static boolean initialized;

    private FabricPortalClientNetworking() {
    }

    public static synchronized void initialize() {
        if (!initialized) {
            if (!ClientPlayNetworking.registerGlobalReceiver(PacketPortalFX.TYPE, (packet, context) -> packet.handle(context.player()))) {
                throw new IllegalStateException("A client receiver is already registered for " + PacketPortalFX.TYPE.id());
            }
            initialized = true;
        }
    }
}
