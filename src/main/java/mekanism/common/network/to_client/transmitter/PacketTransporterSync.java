package mekanism.common.network.to_client.transmitter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.network.PacketUtils;
import mekanism.common.tile.transmitter.TileEntityLogisticalTransporterBase;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record PacketTransporterSync(long pos, int stackId, TransporterStack stack) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PacketTransporterSync> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "transporter_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTransporterSync> STREAM_CODEC = StreamCodec.composite(
          ByteBufCodecs.VAR_LONG, PacketTransporterSync::pos,
          ByteBufCodecs.VAR_INT, PacketTransporterSync::stackId,
          TransporterStack.STREAM_CODEC, PacketTransporterSync::stack,
          PacketTransporterSync::new
    );

    public static PacketTransporterSync create(long pos, int stackId, TransporterStack stack) {
        return new PacketTransporterSync(pos, stackId, stack.updateForPos(pos));
    }

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketTransporterSync> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (PacketUtils.blockEntity(player, pos) instanceof TileEntityLogisticalTransporterBase tile) {
            tile.getTransmitter().addStack(stackId, stack);
        }
    }
}