package mekanism.fabric.network;

import mekanism.common.network.to_client.PacketHitBlockEffect;
import mekanism.common.network.to_client.PacketPortalFX;
import mekanism.common.network.to_client.PacketSetDeltaMovement;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Common-side codecs for teleporter motion and particle and laser hit-effect packets. */
public final class FabricPortalNetworking {
    private static boolean initialized;

    private FabricPortalNetworking() {
    }

    public static synchronized void initialize() {
        if (!initialized) {
            PayloadTypeRegistry.playS2C().register(PacketPortalFX.TYPE, PacketPortalFX.STREAM_CODEC);
            PayloadTypeRegistry.playS2C().register(PacketSetDeltaMovement.TYPE, PacketSetDeltaMovement.STREAM_CODEC);
            PayloadTypeRegistry.playS2C().register(PacketHitBlockEffect.TYPE, PacketHitBlockEffect.STREAM_CODEC);
            initialized = true;
        }
    }
}
