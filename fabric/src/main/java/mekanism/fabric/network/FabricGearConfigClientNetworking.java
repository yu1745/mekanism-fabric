package mekanism.fabric.network;

import mekanism.fabric.config.FabricGearConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Installs the matching connection's gear configuration before acknowledging the server task. */
public final class FabricGearConfigClientNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricGearClient");
    private static Connection activeConnection;
    private static boolean initialized;

    private FabricGearConfigClientNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> begin(handler.connection));
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> disconnect(client, handler.connection));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnect(client, handler.getConnection()));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            synchronized (FabricGearConfigClientNetworking.class) {
                Connection old = activeConnection;
                activeConnection = null;
                if (old != null) FabricGearConfig.detachClient(old);
            }
        });
        if (!ClientConfigurationNetworking.registerGlobalReceiver(GearConfigSnapshotPayload.TYPE, (payload, context) -> {
            Connection connection = context.networkHandler().connection;
            context.client().execute(() -> {
                synchronized (FabricGearConfigClientNetworking.class) {
                    if (activeConnection != connection || !connection.isConnected()) return;
                    try {
                        FabricGearConfig.attachClient(connection, context.client().getSingleplayerServer(), payload.values());
                        context.responseSender().sendPacket(new GearConfigSnapshotPayload.Ack(payload.session()));
                        LOGGER.info("MEKANISM_FABRIC_GEAR_CONFIG_PROOF phase=client_applied session={} values={}", payload.session(), payload.values().size());
                    } catch (RuntimeException failure) {
                        LOGGER.error("Unable to install gear configuration", failure);
                        FabricGearConfig.detachClient(connection);
                        connection.disconnect(Component.literal("Invalid Mekanism gear configuration"));
                    }
                }
            });
        })) throw new IllegalStateException("Gear configuration receiver already registered");
        initialized = true;
    }

    private static synchronized void begin(Connection connection) {
        activeConnection = connection;
    }

    private static void disconnect(Minecraft client, Connection connection) {
        synchronized (FabricGearConfigClientNetworking.class) {
            if (activeConnection == connection) activeConnection = null;
        }
        client.execute(() -> FabricGearConfig.detachClient(connection));
    }
}
