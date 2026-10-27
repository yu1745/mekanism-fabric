package mekanism.fabric.network;

import mekanism.common.network.to_client.PacketHitBlockEffect;
import mekanism.common.network.to_client.PacketPortalFX;
import mekanism.common.network.to_client.PacketSetDeltaMovement;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-side dispatch for teleporter motion and particle and laser hit-effect packets. */
public final class FabricPortalClientNetworking {
    private static boolean initialized;

    private FabricPortalClientNetworking() {
    }

    public static synchronized void initialize() {
        if (!initialized) {
            if (!ClientPlayNetworking.registerGlobalReceiver(PacketPortalFX.TYPE, (packet, context) -> packet.handle(context.player()))) {
                throw new IllegalStateException("A client receiver is already registered for " + PacketPortalFX.TYPE.id());
            }
            if (!ClientPlayNetworking.registerGlobalReceiver(PacketSetDeltaMovement.TYPE, (packet, context) -> packet.handle(context.player()))) {
                throw new IllegalStateException("A client receiver is already registered for " + PacketSetDeltaMovement.TYPE.id());
            }
            if (!ClientPlayNetworking.registerGlobalReceiver(PacketHitBlockEffect.TYPE, (packet, context) -> packet.handle(context.player()))) {
                throw new IllegalStateException("A client receiver is already registered for " + PacketHitBlockEffect.TYPE.id());
            }
            initialized = true;
        }
    }
}
