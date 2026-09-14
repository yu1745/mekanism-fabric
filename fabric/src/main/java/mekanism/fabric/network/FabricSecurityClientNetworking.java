package mekanism.fabric.network;

import mekanism.common.lib.security.ClientSecurityData;
import mekanism.common.network.to_client.security.PacketBatchSecurityUpdate;
import mekanism.common.network.to_client.security.PacketSyncSecurity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

/** Both maps belong to the real connection, survive dimension changes, and mutate only on the client thread. */
public final class FabricSecurityClientNetworking {
    //Guard identity changes as well as map publication against configuration/network thread callbacks.
    private static Connection activeConnection;
    private static boolean initialized;

    private FabricSecurityClientNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> begin(client, handler.connection));
        if (!ClientConfigurationNetworking.registerGlobalReceiver(PacketBatchSecurityUpdate.TYPE, (payload, context) ->
              apply(context.client(), context.networkHandler().connection, payload::handle))) {
            throw new IllegalStateException("Security configuration receiver already registered");
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(PacketSyncSecurity.TYPE, (payload, context) ->
              apply(context.client(), context.player().connection.getConnection(), payload::handle))) {
            throw new IllegalStateException("Security play receiver already registered");
        }
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> disconnect(client, handler.connection));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnect(client, handler.getConnection()));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            synchronized (FabricSecurityClientNetworking.class) {
                activeConnection = null;
                ClientSecurityData.clear();
            }
        });
        initialized = true;
    }

    private static synchronized void begin(Minecraft client, Connection connection) {
        if (activeConnection != connection) {
            activeConnection = connection;
            client.execute(() -> {
                synchronized (FabricSecurityClientNetworking.class) {
                    if (activeConnection == connection) ClientSecurityData.clear();
                }
            });
        }
    }

    private static void apply(Minecraft client, Connection connection, Runnable action) {
        client.execute(() -> {
            synchronized (FabricSecurityClientNetworking.class) {
                if (activeConnection == connection && connection.isConnected()) {
                    action.run();
                }
            }
        });
    }

    private static synchronized void disconnect(Minecraft client, Connection connection) {
        if (activeConnection == connection) {
            activeConnection = null;
            client.execute(() -> {
                synchronized (FabricSecurityClientNetworking.class) {
                    //A queued old disconnect must not clear a new connection's maps.
                    if (activeConnection == null) ClientSecurityData.clear();
                }
            });
        }
    }
}
