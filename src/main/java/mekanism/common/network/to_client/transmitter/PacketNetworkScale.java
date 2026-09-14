package mekanism.common.network.to_client.transmitter;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import mekanism.common.lib.transmitter.DynamicBufferedNetwork;
import mekanism.common.lib.transmitter.DynamicNetwork;
import mekanism.common.lib.transmitter.TransmitterNetworkRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record PacketNetworkScale(UUID networkID, float scale) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PacketNetworkScale> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "network_scale"));
    public static final StreamCodec<ByteBuf, PacketNetworkScale> STREAM_CODEC = StreamCodec.composite(
          UUIDUtil.STREAM_CODEC, PacketNetworkScale::networkID,
          ByteBufCodecs.FLOAT, PacketNetworkScale::scale,
          PacketNetworkScale::new
    );

    public PacketNetworkScale(DynamicBufferedNetwork<?, ?, ?, ?> network) {
        this(network.getUUID(), network.currentScale);
    }

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketNetworkScale> type() {
        return TYPE;
    }

    public void handle() {
        //Note: We set the information even if opaque transmitters is true in case the client turns the config setting off
        // so that they will have the proper information to then render
        DynamicNetwork<?, ?, ?> clientNetwork = TransmitterNetworkRegistry.getClientNetwork(networkID);
        if (clientNetwork instanceof DynamicBufferedNetwork<?, ?, ?, ?> network) {
            network.currentScale = scale;
        }
    }
}