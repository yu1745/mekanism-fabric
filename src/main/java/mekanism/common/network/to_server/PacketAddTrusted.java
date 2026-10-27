package mekanism.common.network.to_server;

import io.netty.buffer.ByteBuf;
import mekanism.common.network.IMekanismPacket;
import mekanism.common.network.PacketUtils;
import mekanism.common.tile.TileEntitySecurityDesk;
import mekanism.common.util.text.InputValidator;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record PacketAddTrusted(BlockPos pos, String name) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketAddTrusted> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "add_trusted"));
    public static final StreamCodec<ByteBuf, PacketAddTrusted> STREAM_CODEC = StreamCodec.composite(
          BlockPos.STREAM_CODEC, PacketAddTrusted::pos,
          ByteBufCodecs.stringUtf8(SharedConstants.MAX_PLAYER_NAME_LENGTH), PacketAddTrusted::name,
          PacketAddTrusted::new
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketAddTrusted> type() {
        return TYPE;
    }

    @Override
    public void handle(Player player) {
        if (!name.isEmpty() && InputValidator.test(name, InputValidator.USERNAME) && PacketUtils.guiTile(player, pos) instanceof TileEntitySecurityDesk desk
              && desk.getFreq() != null && desk.getFreq().ownerMatches(player.getUUID())) {
            desk.addTrusted(name);
        }
    }
}