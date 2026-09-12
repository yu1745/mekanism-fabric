package mekanism.fabric.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import mekanism.api.chemical.ChemicalDataContext;
import mekanism.fabric.datamaps.FabricChemicalDataMaps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded configuration ACK and play-reload synchronization owner. */
public final class FabricChemicalNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricChemicalNetwork");
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type(ChemicalSnapshotPayload.ID.toString());
    private static final AtomicLong NEXT = new AtomicLong();
    private static final Map<ServerConfigurationPacketListenerImpl, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PLAY_SESSIONS = new ConcurrentHashMap<>();
    private static boolean initialized;

    private FabricChemicalNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        PayloadTypeRegistry.configurationS2C().register(ChemicalSnapshotPayload.TYPE, ChemicalSnapshotPayload.CODEC);
        PayloadTypeRegistry.configurationC2S().register(ChemicalSnapshotAckPayload.TYPE, ChemicalSnapshotAckPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChemicalSnapshotPayload.TYPE, ChemicalSnapshotPayload.CODEC);
        if (!ServerConfigurationNetworking.registerGlobalReceiver(ChemicalSnapshotAckPayload.TYPE, FabricChemicalNetworking::ack)) {
            throw new IllegalStateException("Chemical snapshot ACK receiver already registered");
        }
        ServerConfigurationConnectionEvents.CONFIGURE.register(FabricChemicalNetworking::configure);
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PLAY_SESSIONS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { PENDING.entrySet().removeIf(e -> e.getValue().server == server); PLAY_SESSIONS.clear(); });
        initialized = true;
    }

    private static void configure(ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        if (!ServerConfigurationNetworking.canSend(handler, FabricMekanismNetworking.REQUEST_TYPE)) {
            // The earlier mandatory protocol owner always retains the canonical missing-receiver rejection.
            return;
        }
        String session = Long.toUnsignedString(NEXT.incrementAndGet(), 36) + "-" + UUID.randomUUID();
        Pending pending = new Pending(server, session);
        if (PENDING.putIfAbsent(handler, pending) != null) {
            handler.disconnect(Component.literal("Duplicate Mekanism chemical snapshot task"));
            return;
        }
        PLAY_SESSIONS.remove(handler.getOwner().getId());
        ((FabricServerConfigurationNetworkHandler) handler).addTask(new SnapshotTask(handler, pending));
    }

    private static void ack(ChemicalSnapshotAckPayload ack, ServerConfigurationNetworking.Context context) {
        ServerConfigurationPacketListenerImpl handler = context.networkHandler();
        Pending pending = PENDING.get(handler);
        if (pending == null) {
            handler.disconnect(Component.literal("Unexpected Mekanism chemical snapshot acknowledgement"));
            return;
        }
        pending.server.execute(() -> {
            if (PENDING.remove(handler, pending) && pending.session.equals(ack.sessionId()) && pending.epoch == ack.epoch() && pending.epoch > 0) {
                PLAY_SESSIONS.put(handler.getOwner().getId(), pending.session);
                ((FabricServerConfigurationNetworkHandler) handler).completeTask(TASK);
                LOGGER.info("MEKANISM_FABRIC_CHEMICAL_ACK session={} epoch={}", pending.session, pending.epoch);
            } else {
                handler.disconnect(Component.literal("Rejected stale or malformed Mekanism chemical snapshot acknowledgement"));
            }
        });
    }

    public static void broadcastReload(MinecraftServer server, ChemicalDataContext context) {
        if (!server.isSameThread()) throw new IllegalStateException("Chemical reload broadcast must run on server thread");
        for (var player : server.getPlayerList().getPlayers()) {
            String session = PLAY_SESSIONS.get(player.getUUID());
            if (session != null && ServerPlayNetworking.canSend(player, ChemicalSnapshotPayload.TYPE)) {
                ServerPlayNetworking.send(player, ChemicalSnapshotPayload.from(session, context));
            }
        }
    }

    private static final class Pending {
        final MinecraftServer server;
        final String session;
        volatile long epoch;
        Pending(MinecraftServer server, String session) { this.server = server; this.session = session; }
    }

    private record SnapshotTask(ServerConfigurationPacketListenerImpl handler, Pending pending) implements ConfigurationTask {
        @Override public void start(Consumer<Packet<?>> sender) {
            if (!ServerConfigurationNetworking.canSend(handler, ChemicalSnapshotPayload.TYPE)) {
                PENDING.remove(handler, pending);
                handler.disconnect(Component.literal("This server requires Mekanism chemical snapshot support"));
                return;
            }
            pending.server.execute(() -> {
                if (PENDING.get(handler) != pending) return;
                ChemicalDataContext context = FabricChemicalDataMaps.session(pending.server).context();
                pending.epoch = context.epoch();
                sender.accept(ServerConfigurationNetworking.createS2CPacket(ChemicalSnapshotPayload.from(pending.session, context)));
            });
        }
        @Override public Type type() { return TASK; }
    }
}
