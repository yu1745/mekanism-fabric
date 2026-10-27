package mekanism.fixture;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Independent client fixture that deliberately rejects Mekanism's configuration protocol. */
public final class MismatchedHandshakeClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/HandshakeMismatchFixture");
    private static final String CURRENT_PROTOCOL = "10.7.19";
    private static final String MISMATCH_PROTOCOL = "fixture-mismatch-0";
    private static final String MODE_PROPERTY = "mekanism.handshake.fixtureMode";
    private static final FixtureMode MODE = FixtureMode.parse(System.getProperty(MODE_PROPERTY, "version_mismatch"));
    private static final int MAX_VERSION_LENGTH = 32;
    private static final CustomPacketPayload.Type<VersionRequest> REQUEST_TYPE = new CustomPacketPayload.Type<>(
          ResourceLocation.fromNamespaceAndPath("mekanism", "network_version")
    );
    private static final CustomPacketPayload.Type<VersionResponse> RESPONSE_TYPE = new CustomPacketPayload.Type<>(
          ResourceLocation.fromNamespaceAndPath("mekanism", "network_version_response")
    );
    private static final StreamCodec<FriendlyByteBuf, VersionRequest> REQUEST_CODEC = StreamCodec.composite(
          ByteBufCodecs.stringUtf8(MAX_VERSION_LENGTH), VersionRequest::protocolVersion, VersionRequest::new
    );
    private static final StreamCodec<FriendlyByteBuf, VersionResponse> RESPONSE_CODEC = StreamCodec.composite(
          ByteBufCodecs.stringUtf8(MAX_VERSION_LENGTH), VersionResponse::protocolVersion,
          ByteBufCodecs.BOOL, VersionResponse::accepted, VersionResponse::new
    );

    @Override
    public void onInitializeClient() {
        registerGameEventsForRegistrySync();
        PayloadTypeRegistry.configurationS2C().register(REQUEST_TYPE, REQUEST_CODEC);
        PayloadTypeRegistry.configurationC2S().register(RESPONSE_TYPE, RESPONSE_CODEC);
        if (!ClientConfigurationNetworking.registerGlobalReceiver(REQUEST_TYPE, MismatchedHandshakeClient::handleRequest)) {
            throw new IllegalStateException("Mismatch fixture receiver is already registered");
        }
        LOGGER.info("MEKANISM_FIXTURE_MISMATCH_READY mode={} local={} registry_entries=4", MODE.id, MODE.localProtocol);
    }

    private static void registerGameEventsForRegistrySync() {
        registerGameEvent("seismic_vibration", 64);
        registerGameEvent("jetpack_burn", 16);
        registerGameEvent("gravity_modulate", 16);
        registerGameEvent("gravity_modulate_boosted", 32);
    }

    private static void registerGameEvent(String path, int radius) {
        Registry.register(BuiltInRegistries.GAME_EVENT,
              ResourceLocation.fromNamespaceAndPath("mekanism", path), new GameEvent(radius));
    }

    private static void handleRequest(VersionRequest request, ClientConfigurationNetworking.Context context) {
        boolean accepted = MODE.accept && MODE.localProtocol.equals(request.protocolVersion());
        LOGGER.info("MEKANISM_FIXTURE_MISMATCH_RESPONSE mode={} local={} remote={} accepted={}",
              MODE.id, MODE.localProtocol, request.protocolVersion(), accepted);
        context.responseSender().sendPacket(new VersionResponse(MODE.localProtocol, accepted));
    }

    private enum FixtureMode {
        VERSION_MISMATCH("version_mismatch", MISMATCH_PROTOCOL, true),
        SAME_VERSION_REJECT("same_version_reject", CURRENT_PROTOCOL, false);

        private final String id;
        private final String localProtocol;
        private final boolean accept;

        FixtureMode(String id, String localProtocol, boolean accept) {
            this.id = id;
            this.localProtocol = localProtocol;
            this.accept = accept;
        }

        private static FixtureMode parse(String value) {
            for (FixtureMode mode : values()) {
                if (mode.id.equals(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unsupported " + MODE_PROPERTY + ": " + value
                  + " (expected version_mismatch or same_version_reject)");
        }
    }

    private record VersionRequest(String protocolVersion) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return REQUEST_TYPE;
        }
    }

    private record VersionResponse(String protocolVersion, boolean accepted) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return RESPONSE_TYPE;
        }
    }
}
