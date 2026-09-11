package mekanism.fabric.network;

import java.util.Optional;
import java.util.function.Consumer;
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

/** Common and server-safe Fabric networking bootstrap. */
public final class FabricMekanismNetworking {
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
        ServerConfigurationConnectionEvents.CONFIGURE.register(FabricMekanismNetworking::beginHandshake);
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> HANDSHAKES.disconnect(handler));
        registration = new Registration(MekanismNetworkProtocol.CURRENT, 2, true, true);
        return registration;
    }

    private static void beginHandshake(ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        if (!ServerConfigurationNetworking.canSend(handler, REQUEST_TYPE)) {
            handler.disconnect(Component.literal("This server requires Mekanism protocol " + MekanismNetworkProtocol.CURRENT));
            return;
        }
        ((FabricServerConfigurationNetworkHandler) handler).addTask(new VersionNegotiationTask(handler));
    }

    private static void handleResponse(VersionResponse response, ServerConfigurationNetworking.Context context) {
        ServerConfigurationPacketListenerImpl handler = context.networkHandler();
        Optional<MekanismNetworkProtocol.Compatibility> result = HANDSHAKES.acknowledge(
              handler, response.protocolVersion(), response.accepted());
        if (result.isEmpty()) {
            handler.disconnect(Component.literal("Unexpected Mekanism protocol response"));
            return;
        }
        MekanismNetworkProtocol.Compatibility compatibility = result.orElseThrow();
        if (!compatibility.compatible()) {
            handler.disconnect(Component.literal(compatibility.disconnectReason()));
            return;
        }
        ((FabricServerConfigurationNetworkHandler) handler).completeTask(VERSION_TASK_TYPE);
    }

    private record VersionNegotiationTask(ServerConfigurationPacketListenerImpl handler) implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> packetSender) {
            if (!HANDSHAKES.begin(handler)) {
                handler.disconnect(Component.literal("Duplicate Mekanism protocol negotiation"));
                return;
            }
            try {
                packetSender.accept(ServerConfigurationNetworking.createS2CPacket(
                      new VersionRequest(MekanismNetworkProtocol.CURRENT)
                ));
            } catch (RuntimeException | Error e) {
                HANDSHAKES.disconnect(handler);
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
