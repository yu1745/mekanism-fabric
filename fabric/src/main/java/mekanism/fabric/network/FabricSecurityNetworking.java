package mekanism.fabric.network;

import mekanism.api.MekanismServerLifecycle;
import mekanism.common.network.to_client.configuration.SyncAllSecurityData;
import mekanism.common.network.to_client.security.PacketBatchSecurityUpdate;
import mekanism.common.network.to_client.security.PacketSyncSecurity;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;

/** Canonical registration, configuration task and broadcasts for the original security payloads. */
public final class FabricSecurityNetworking {
    private static boolean initialized;

    private FabricSecurityNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        PayloadTypeRegistry.configurationS2C().register(PacketBatchSecurityUpdate.TYPE, PacketBatchSecurityUpdate.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketSyncSecurity.TYPE, PacketSyncSecurity.STREAM_CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (!ServerConfigurationNetworking.canSend(handler, FabricMekanismNetworking.REQUEST_TYPE)) {
                //Retain the mandatory protocol owner's missing-client rejection.
                return;
            }
            if (!ServerConfigurationNetworking.canSend(handler, PacketBatchSecurityUpdate.TYPE)) {
                handler.disconnect(Component.literal("This server requires Mekanism security synchronization"));
                return;
            }
            ((FabricServerConfigurationNetworkHandler) handler).addTask(new SyncAllSecurityData(handler, server));
        });
        initialized = true;
    }

    public static void broadcast(PacketSyncSecurity packet) {
        var server = MekanismServerLifecycle.getServer();
        if (server == null) {
            throw new IllegalStateException("Security broadcast requires a running server");
        }
        for (var player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, packet);
        }
    }
}
