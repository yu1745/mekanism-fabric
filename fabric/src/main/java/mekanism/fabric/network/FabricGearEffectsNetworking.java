package mekanism.fabric.network;

import mekanism.common.network.to_client.PacketLightningRender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Actual gear-effect packet registration and original entity-tracking-plus-self recipients. */
public final class FabricGearEffectsNetworking {
    private static boolean initialized;

    private FabricGearEffectsNetworking() {
    }

    public static synchronized void initialize() {
        if (!initialized) {
            PayloadTypeRegistry.playS2C().register(PacketLightningRender.TYPE, PacketLightningRender.STREAM_CODEC);
            initialized = true;
        }
    }

    public static void sendTrackingAndSelf(Player player, PacketLightningRender payload) {
        if (player instanceof ServerPlayer serverPlayer) {
            for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
                if (tracking != serverPlayer) {
                    ServerPlayNetworking.send(tracking, payload);
                }
            }
            ServerPlayNetworking.send(serverPlayer, payload);
        }
    }
}
