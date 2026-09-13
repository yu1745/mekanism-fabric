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
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> {
            boolean pendingRemoved = PENDING.remove(handler) != null;
            boolean playSessionRemoved = PLAY_SESSIONS.remove(handler.getOwner().getId()) != null;
            LOGGER.info("MEKANISM_FABRIC_CHEMICAL_CONFIGURATION_DISCONNECT player={} pending_removed={} play_session_removed={}",
                  handler.getOwner().getName(), pendingRemoved, playSessionRemoved);
        });
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
            if (PENDING.get(handler) != pending) {
                handler.disconnect(Component.literal("Rejected stale or malformed Mekanism chemical snapshot acknowledgement"));
                return;
            }
            try {
                ChemicalDataContext authoritative = FabricChemicalDataMaps.session(pending.server).context();
                AckDecision decision = decideAck(pending.session, pending.epoch, ack, authoritative.epoch());
                if (decision == AckDecision.RESEND) {
                    pending.send(authoritative);
                    LOGGER.info("MEKANISM_FABRIC_CHEMICAL_CATCH_UP session={} epoch={}", pending.session, pending.epoch);
                    return;
                }
                if (!PENDING.remove(handler, pending)) {
                    throw new IllegalStateException("Chemical snapshot task disappeared before completion");
                }
                PLAY_SESSIONS.put(handler.getOwner().getId(), pending.session);
                ((FabricServerConfigurationNetworkHandler) handler).completeTask(TASK);
                LOGGER.info("MEKANISM_FABRIC_CHEMICAL_ACK session={} epoch={}", pending.session, pending.epoch);
            } catch (RuntimeException failure) {
                PENDING.remove(handler, pending);
                handler.disconnect(Component.literal("Rejected stale or malformed Mekanism chemical snapshot acknowledgement"));
            }
        });
    }

    /** Package-private executable-fixture seam; production cleanup still owns all mutation. */
    static boolean hasPlaySession(UUID playerId) {
        return PLAY_SESSIONS.containsKey(playerId);
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

    static AckDecision decideAck(String pendingSession, long pendingEpoch, ChemicalSnapshotAckPayload ack, long authoritativeEpoch) {
        if (pendingEpoch <= 0 || !pendingSession.equals(ack.sessionId()) || pendingEpoch != ack.epoch()
              || authoritativeEpoch < pendingEpoch) {
            throw new IllegalArgumentException("Stale or malformed chemical snapshot acknowledgement");
        }
        return authoritativeEpoch > pendingEpoch ? AckDecision.RESEND : AckDecision.COMPLETE;
    }

    enum AckDecision { COMPLETE, RESEND }

    private static final class Pending {
        final MinecraftServer server;
        final String session;
        long epoch;
        Consumer<Packet<?>> sender;
        Pending(MinecraftServer server, String session) { this.server = server; this.session = session; }

        void send(ChemicalDataContext context) {
            if (!server.isSameThread()) throw new IllegalStateException("Chemical configuration snapshot must be sent on the server thread");
            if (context.epoch() <= epoch) throw new IllegalArgumentException("Chemical configuration epoch must increase");
            epoch = context.epoch();
            sender.accept(ServerConfigurationNetworking.createS2CPacket(ChemicalSnapshotPayload.from(session, context)));
        }
    }

    private record SnapshotTask(ServerConfigurationPacketListenerImpl handler, Pending pending) implements ConfigurationTask {
        @Override public void start(Consumer<Packet<?>> sender) {
            if (!ServerConfigurationNetworking.canSend(handler, ChemicalSnapshotPayload.TYPE)) {
                PENDING.remove(handler, pending);
                handler.disconnect(Component.literal("This server requires Mekanism chemical snapshot support"));
                return;
            }
            pending.sender = sender;
            pending.server.execute(() -> {
                if (PENDING.get(handler) != pending) return;
                pending.send(FabricChemicalDataMaps.session(pending.server).context());
            });
        }
        @Override public Type type() { return TASK; }
    }
}
