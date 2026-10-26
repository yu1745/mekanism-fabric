package mekanism.common.network.to_server.configuration_update;

import io.netty.buffer.ByteBuf;
import mekanism.api.RelativeSide;
import mekanism.common.network.IMekanismPacket;
import mekanism.common.network.MekClickType;
import mekanism.common.network.PacketUtils;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.util.TransporterUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record PacketInputColor(BlockPos pos, MekClickType clickType, RelativeSide inputSide) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketInputColor> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "input_color"));
    public static final StreamCodec<ByteBuf, PacketInputColor> STREAM_CODEC = StreamCodec.composite(
          BlockPos.STREAM_CODEC, PacketInputColor::pos,
          MekClickType.STREAM_CODEC, PacketInputColor::clickType,
          RelativeSide.STREAM_CODEC, PacketInputColor::inputSide,
          PacketInputColor::new
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketInputColor> type() {
        return TYPE;
    }

    @Override
    public void handle(Player player) {
        TileComponentEjector ejector = PacketUtils.ejector(player, pos);
        if (ejector != null) {
            ejector.setInputColor(inputSide, switch (clickType) {
                case LEFT -> TransporterUtils.increment(ejector.getInputColor(inputSide));
                case RIGHT -> TransporterUtils.decrement(ejector.getInputColor(inputSide));
                case SHIFT_LEFT -> null;
            });
        }
    }
}