package mekanism.generators.common.network;

import mekanism.common.network.IMekanismPacket;
import mekanism.generators.common.network.to_server.PacketGeneratorsGuiInteract;
import mekanism.generators.common.network.to_server.PacketGeneratorsTileButtonPress;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class GeneratorsPacketHandler {
    public GeneratorsPacketHandler() {
        register(PacketGeneratorsTileButtonPress.TYPE, PacketGeneratorsTileButtonPress.STREAM_CODEC);
        register(PacketGeneratorsGuiInteract.TYPE, PacketGeneratorsGuiInteract.STREAM_CODEC);
    }

    private static <T extends IMekanismPacket> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.playC2S().register(type, codec);
        if (!ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> payload.handle(context.player()))) {
            throw new IllegalStateException("A server play receiver is already registered for " + type.id());
        }
    }
}
