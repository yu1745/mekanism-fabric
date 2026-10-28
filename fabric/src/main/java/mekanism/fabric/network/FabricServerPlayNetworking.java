package mekanism.fabric.network;

import java.util.List;
import mekanism.common.network.IMekanismPacket;
import mekanism.common.network.to_server.PacketAddTrusted;
import mekanism.common.network.to_server.PacketDropperUse;
import mekanism.common.network.to_server.PacketGuiSetEnergy;
import mekanism.common.network.to_server.PacketGuiInteract;
import mekanism.common.network.to_server.PacketGearStateUpdate;
import mekanism.common.network.to_server.PacketKey;
import mekanism.common.network.to_server.PacketModeChange;
import mekanism.common.network.to_server.PacketOpenGui;
import mekanism.common.network.to_server.PacketPortableTeleporterTeleport;
import mekanism.common.network.to_server.PacketRadialModeChange;
import mekanism.common.network.to_server.PacketRemoveModule;
import mekanism.common.network.to_server.PacketUpdateModuleSettings;
import mekanism.common.network.to_server.button.PacketEntityButtonPress;
import mekanism.common.network.to_server.button.PacketItemButtonPress;
import mekanism.common.network.to_server.button.PacketTileButtonPress;
import mekanism.common.network.to_server.configuration_update.PacketBatchConfiguration;
import mekanism.common.network.to_server.configuration_update.PacketEjectColor;
import mekanism.common.network.to_server.configuration_update.PacketEjectConfiguration;
import mekanism.common.network.to_server.configuration_update.PacketInputColor;
import mekanism.common.network.to_server.configuration_update.PacketSideData;
import mekanism.common.network.to_server.filter.PacketEditFilter;
import mekanism.common.network.to_server.filter.PacketNewFilter;
import mekanism.common.network.to_server.frequency.PacketSetFrequencyColor;
import mekanism.common.network.to_server.frequency.PacketSetItemFrequency;
import mekanism.common.network.to_server.frequency.PacketSetTileFrequency;
import mekanism.common.network.to_server.robit.PacketRobitName;
import mekanism.common.network.to_server.robit.PacketRobitSkin;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side play channel for the official client-to-server packets.
 *
 * <p>NeoForge's {@code IPayloadContext} handlers are replaced by {@link IMekanismPacket#handle(net.minecraft.world.entity.player.Player)}: Fabric's play receiver
 * already resolves the sending player, and the handler runs on the server thread. Registration is common-safe so an integrated server receives the same payloads
 * as a dedicated server; the client only sends them.
 */
public final class FabricServerPlayNetworking {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricNetwork");
    private static int payloadCount;

    private static boolean initialized;

    private FabricServerPlayNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        register(PacketTileButtonPress.TYPE, PacketTileButtonPress.STREAM_CODEC);
        register(PacketItemButtonPress.TYPE, PacketItemButtonPress.STREAM_CODEC);
        register(PacketEntityButtonPress.TYPE, PacketEntityButtonPress.STREAM_CODEC);
        register(PacketBatchConfiguration.TYPE, PacketBatchConfiguration.STREAM_CODEC);
        register(PacketSideData.TYPE, PacketSideData.STREAM_CODEC);
        register(PacketInputColor.TYPE, PacketInputColor.STREAM_CODEC);
        register(PacketEjectConfiguration.TYPE, PacketEjectConfiguration.STREAM_CODEC);
        register(PacketEjectColor.TYPE, PacketEjectColor.STREAM_CODEC);
        register(PacketNewFilter.TYPE, PacketNewFilter.STREAM_CODEC);
        register(PacketEditFilter.TYPE, PacketEditFilter.STREAM_CODEC);
        register(PacketSetTileFrequency.TYPE, PacketSetTileFrequency.STREAM_CODEC);
        register(PacketSetItemFrequency.TYPE, PacketSetItemFrequency.STREAM_CODEC);
        register(PacketSetFrequencyColor.TYPE, PacketSetFrequencyColor.STREAM_CODEC);
        register(PacketAddTrusted.TYPE, PacketAddTrusted.STREAM_CODEC);
        register(PacketOpenGui.TYPE, PacketOpenGui.STREAM_CODEC);
        register(PacketRemoveModule.TYPE, PacketRemoveModule.STREAM_CODEC);
        register(PacketUpdateModuleSettings.TYPE, PacketUpdateModuleSettings.STREAM_CODEC);
        register(PacketGuiSetEnergy.TYPE, PacketGuiSetEnergy.STREAM_CODEC);
        register(PacketGuiInteract.TYPE, PacketGuiInteract.STREAM_CODEC);
        register(PacketGearStateUpdate.TYPE, PacketGearStateUpdate.STREAM_CODEC);
        register(PacketRadialModeChange.TYPE, PacketRadialModeChange.STREAM_CODEC);
        register(PacketModeChange.TYPE, PacketModeChange.STREAM_CODEC);
        register(PacketKey.TYPE, PacketKey.STREAM_CODEC);
        register(PacketDropperUse.TYPE, PacketDropperUse.STREAM_CODEC);
        register(PacketPortableTeleporterTeleport.TYPE, PacketPortableTeleporterTeleport.STREAM_CODEC);
        register(PacketRobitName.TYPE, PacketRobitName.STREAM_CODEC);
        register(PacketRobitSkin.TYPE, PacketRobitSkin.STREAM_CODEC);
        initialized = true;
        LOGGER.info("MEKANISM_FABRIC_SERVER_PLAY_PROOF payloads={} direction=C2S thread=server", payloadCount);
    }

    private static <T extends IMekanismPacket> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.playC2S().register(type, codec);
        if (!ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> payload.handle(context.player()))) {
            throw new IllegalStateException("A server play receiver is already registered for " + type.id());
        }
        payloadCount++;
    }
}
