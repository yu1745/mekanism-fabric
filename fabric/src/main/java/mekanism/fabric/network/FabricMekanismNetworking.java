package mekanism.fabric.network;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common and server-safe Fabric networking bootstrap. */
public final class FabricMekanismNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricNetwork");
    static final ResourceLocation REQUEST_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "network_version");
    static final ResourceLocation RESPONSE_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "network_version_response");
    static final CustomPacketPayload.Type<VersionRequest> REQUEST_TYPE = new CustomPacketPayload.Type<>(REQUEST_ID);
    static final CustomPacketPayload.Type<VersionResponse> RESPONSE_TYPE = new CustomPacketPayload.Type<>(RESPONSE_ID);
    static final StreamCodec<FriendlyByteBuf, VersionRequest> REQUEST_CODEC = StreamCodec.composite(
          ByteBufCodecs.stringUtf8(MekanismNetworkProtocol.MAX_VERSION_LENGTH), VersionRequest::protocolVersion, VersionRequest::new
    );
    static final StreamCodec<FriendlyByteBuf, VersionResponse> RESPONSE_CODEC = StreamCodec.composite(
          ByteBufCodecs.stringUtf8(MekanismNetworkProtocol.MAX_VERSION_LENGTH), VersionResponse::protocolVersion,
          ByteBufCodecs.BOOL, VersionResponse::accepted, VersionResponse::new
    );
    private static final ConfigurationTask.Type VERSION_TASK_TYPE = new ConfigurationTask.Type(REQUEST_ID.toString());
    private static final MekanismNetworkProtocol.HandshakeTracker<ServerConfigurationPacketListenerImpl> HANDSHAKES =
          new MekanismNetworkProtocol.HandshakeTracker<>();
    private static final Map<ServerConfigurationPacketListenerImpl, Long> ACTIVE_SESSION_IDS = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_SESSION_ID = new AtomicLong();
    private static Registration registration;

    private FabricMekanismNetworking() {
    }

    public static synchronized Registration initialize() {
        if (registration != null) {
            return registration;
        }
        PayloadTypeRegistry.configurationS2C().register(REQUEST_TYPE, REQUEST_CODEC);
        PayloadTypeRegistry.configurationC2S().register(RESPONSE_TYPE, RESPONSE_CODEC);
        if (!ServerConfigurationNetworking.registerGlobalReceiver(
              RESPONSE_TYPE, FabricMekanismNetworking::handleResponse)) {
            throw new IllegalStateException("A server receiver is already registered for " + RESPONSE_ID);
        }
        //Registry sync also queues BEFORE_CONFIGURE tasks in the default phase. Negotiate first so an incompatible
        //client receives our protocol rejection before Fabric attempts to remap its deliberately different catalog.
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.addPhaseOrdering(REQUEST_ID, Event.DEFAULT_PHASE);
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register(REQUEST_ID, FabricMekanismNetworking::beginHandshake);
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> {
            HANDSHAKES.disconnect(handler);
            ACTIVE_SESSION_IDS.remove(handler);
        });
        registration = new Registration(MekanismNetworkProtocol.CURRENT, 2, true, true);
        return registration;
    }

    private static void beginHandshake(ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        if (!ServerConfigurationNetworking.canSend(handler, REQUEST_TYPE)) {
            String reason = "This server requires Mekanism protocol " + MekanismNetworkProtocol.CURRENT;
            LOGGER.info("MEKANISM_FABRIC_NETWORK_HANDSHAKE_REJECT kind=missing_receiver player={} protocol={} reason={}",
                  handler.getOwner().getName(), MekanismNetworkProtocol.CURRENT, reason);
            handler.disconnect(Component.literal(reason));
            return;
        }
        ((FabricServerConfigurationNetworkHandler) handler).addTask(
              new VersionNegotiationTask(handler, NEXT_SESSION_ID.incrementAndGet()));
    }

    private static void handleResponse(VersionResponse response, ServerConfigurationNetworking.Context context) {
        ServerConfigurationPacketListenerImpl handler = context.networkHandler();
        Long sessionId = ACTIVE_SESSION_IDS.remove(handler);
        Optional<MekanismNetworkProtocol.Compatibility> result = HANDSHAKES.acknowledge(
              handler, response.protocolVersion(), response.accepted());
        if (sessionId == null || result.isEmpty()) {
            handler.disconnect(Component.literal("Unexpected Mekanism protocol response"));
            return;
        }
        MekanismNetworkProtocol.Compatibility compatibility = result.orElseThrow();
        if (!compatibility.compatible()) {
            String rejectionKind = MekanismNetworkProtocol.CURRENT.equals(response.protocolVersion()) ? "client_rejected" : "version_mismatch";
            LOGGER.info("MEKANISM_FABRIC_NETWORK_HANDSHAKE_REJECT kind={} player={} session={} local={} remote={} reason={}",
                  rejectionKind, handler.getOwner().getName(), sessionId, MekanismNetworkProtocol.CURRENT,
                  MekanismNetworkProtocol.sanitizeForLog(response.protocolVersion()),
                  MekanismNetworkProtocol.sanitizeForLog(compatibility.disconnectReason()));
            handler.disconnect(Component.literal(compatibility.disconnectReason()));
            return;
        }
        ((FabricServerConfigurationNetworkHandler) handler).completeTask(VERSION_TASK_TYPE);
        LOGGER.info("MEKANISM_FABRIC_NETWORK_HANDSHAKE_COMPLETE session={} protocol={} thread={}",
              sessionId, MekanismNetworkProtocol.CURRENT, Thread.currentThread().getName());
    }

    private record VersionNegotiationTask(ServerConfigurationPacketListenerImpl handler, long sessionId) implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> packetSender) {
            if (!HANDSHAKES.begin(handler) || ACTIVE_SESSION_IDS.putIfAbsent(handler, sessionId) != null) {
                HANDSHAKES.disconnect(handler);
                ACTIVE_SESSION_IDS.remove(handler, sessionId);
                handler.disconnect(Component.literal("Duplicate Mekanism protocol negotiation"));
                return;
            }
            try {
                LOGGER.info("MEKANISM_FABRIC_NETWORK_HANDSHAKE_START session={} protocol={} thread={}",
                      sessionId, MekanismNetworkProtocol.CURRENT, Thread.currentThread().getName());
                packetSender.accept(ServerConfigurationNetworking.createS2CPacket(
                      new VersionRequest(MekanismNetworkProtocol.CURRENT)
                ));
            } catch (RuntimeException | Error e) {
                HANDSHAKES.disconnect(handler);
                ACTIVE_SESSION_IDS.remove(handler, sessionId);
                throw e;
            }
        }

        @Override
        public Type type() {
            return VERSION_TASK_TYPE;
        }
    }

    record VersionRequest(String protocolVersion) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return REQUEST_TYPE;
        }
    }

    record VersionResponse(String protocolVersion, boolean accepted) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return RESPONSE_TYPE;
        }
    }

    public record Registration(String protocolVersion, int codecs, boolean serverReceiver, boolean clientRequired) {
    }
}
