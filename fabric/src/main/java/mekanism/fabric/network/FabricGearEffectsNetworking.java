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
            PayloadTypeRegistry.playS2C().register(mekanism.common.network.to_client.player_data.PacketPlayerData.TYPE,
                  mekanism.common.network.to_client.player_data.PacketPlayerData.STREAM_CODEC);
            PayloadTypeRegistry.playS2C().register(mekanism.common.network.to_client.player_data.PacketResetPlayerClient.TYPE,
                  mekanism.common.network.to_client.player_data.PacketResetPlayerClient.STREAM_CODEC);
            net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((tracked, observer) -> {
                if (tracked instanceof ServerPlayer player) {
                    ServerPlayNetworking.send(observer, new mekanism.common.network.to_client.player_data.PacketPlayerData(player.getUUID()));
                }
            });
            net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                  mekanism.common.base.PlayerState.INSTANCE.clearPlayer(player.getUUID(), false));
            net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                  mekanism.common.network.PacketUtils.sendToAllPlayers(new mekanism.common.network.to_client.player_data.PacketResetPlayerClient(newPlayer.getUUID())));
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
                var uuid = handler.player.getUUID();
                server.execute(() -> mekanism.common.base.PlayerState.INSTANCE.clearPlayer(uuid, false));
            });
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
