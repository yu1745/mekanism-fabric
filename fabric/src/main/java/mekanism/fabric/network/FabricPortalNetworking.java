package mekanism.fabric.network;

import mekanism.common.network.to_client.PacketPortalFX;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Common-side codec registration for the official teleporter particle packet. */
public final class FabricPortalNetworking {
    private static boolean initialized;

    private FabricPortalNetworking() {
    }

    public static synchronized void initialize() {
        if (!initialized) {
            PayloadTypeRegistry.playS2C().register(PacketPortalFX.TYPE, PacketPortalFX.STREAM_CODEC);
            initialized = true;
        }
    }
}
