package mekanism.fabric.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import mekanism.common.config.MekanismConfig;
import mekanism.fabric.config.FabricGearConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sends the actual world gear configuration and waits for installation before PLAY. */
public final class FabricGearConfigNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricGearNetwork");
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type(GearConfigSnapshotPayload.TYPE.id().toString());
    private static final Map<ServerConfigurationPacketListenerImpl, Pending> PENDING = new ConcurrentHashMap<>();
    private static boolean initialized;

    private FabricGearConfigNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        PayloadTypeRegistry.configurationS2C().register(GearConfigSnapshotPayload.TYPE, GearConfigSnapshotPayload.CODEC);
        PayloadTypeRegistry.configurationC2S().register(GearConfigSnapshotPayload.Ack.TYPE, GearConfigSnapshotPayload.Ack.CODEC);
        if (!ServerConfigurationNetworking.registerGlobalReceiver(GearConfigSnapshotPayload.Ack.TYPE, FabricGearConfigNetworking::ack)) {
            throw new IllegalStateException("Gear config acknowledgement receiver already registered");
        }
        ServerConfigurationConnectionEvents.CONFIGURE.register(FabricGearConfigNetworking::configure);
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.entrySet().removeIf(entry -> entry.getValue().server == server));
        initialized = true;
    }

    private static void configure(ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        if (!ServerConfigurationNetworking.canSend(handler, FabricMekanismNetworking.REQUEST_TYPE)) return;
        Pending pending = new Pending(server, UUID.randomUUID().toString());
        if (PENDING.putIfAbsent(handler, pending) != null) {
            handler.disconnect(Component.literal("Duplicate Mekanism gear configuration task"));
            return;
        }
        ((FabricServerConfigurationNetworkHandler) handler).addTask(new SnapshotTask(handler, pending));
    }

    private static void ack(GearConfigSnapshotPayload.Ack ack, ServerConfigurationNetworking.Context context) {
        ServerConfigurationPacketListenerImpl handler = context.networkHandler();
        Pending pending = PENDING.get(handler);
        if (pending == null) {
            handler.disconnect(Component.literal("Unexpected Mekanism gear configuration acknowledgement"));
            return;
        }
        pending.server.execute(() -> {
            if (!ack.session().equals(pending.session) || !pending.sent || !PENDING.remove(handler, pending)) {
                handler.disconnect(Component.literal("Invalid Mekanism gear configuration acknowledgement"));
                return;
            }
            ((FabricServerConfigurationNetworkHandler) handler).completeTask(TASK);
            LOGGER.info("MEKANISM_FABRIC_GEAR_CONFIG_PROOF phase=ack session={}", pending.session);
        });
    }

    private static final class Pending {
        final MinecraftServer server;
        final String session;
        boolean sent;
        Pending(MinecraftServer server, String session) {
            this.server = server;
            this.session = session;
        }
    }

    private record SnapshotTask(ServerConfigurationPacketListenerImpl handler, Pending pending) implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> sender) {
            if (!ServerConfigurationNetworking.canSend(handler, GearConfigSnapshotPayload.TYPE)) {
                PENDING.remove(handler, pending);
                handler.disconnect(Component.literal("This server requires Mekanism gear configuration support"));
                return;
            }
            pending.server.execute(() -> {
                if (PENDING.get(handler) != pending) return;
                try {
                    if (!FabricGearConfig.isOwner(pending.server)) throw new IllegalStateException("No server gear config owner");
                    var payload = new GearConfigSnapshotPayload(pending.session, MekanismConfig.gear.getConfigSpec().scalarSnapshot());
                    pending.sent = true;
                    sender.accept(ServerConfigurationNetworking.createS2CPacket(payload));
                    LOGGER.info("MEKANISM_FABRIC_GEAR_CONFIG_PROOF phase=send session={} values={}", pending.session, payload.values().size());
                } catch (RuntimeException failure) {
                    LOGGER.error("Unable to send gear configuration", failure);
                    PENDING.remove(handler, pending);
                    handler.disconnect(Component.literal("Unable to synchronize Mekanism gear configuration"));
                }
            });
        }

        @Override
        public Type type() {
            return TASK;
        }
    }
}
