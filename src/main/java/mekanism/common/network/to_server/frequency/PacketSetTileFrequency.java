package mekanism.common.network.to_server.frequency;

import mekanism.api.security.IBlockSecurityUtils;
import mekanism.common.lib.frequency.Frequency.FrequencyIdentity;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.lib.frequency.IFrequencyHandler;
import mekanism.common.network.IMekanismPacket;
import mekanism.common.network.PacketUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public record PacketSetTileFrequency(boolean set, TypedIdentity data, BlockPos pos) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketSetTileFrequency> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "set_tile_frequency"));
    public static final StreamCodec<FriendlyByteBuf, PacketSetTileFrequency> STREAM_CODEC = StreamCodec.composite(
          ByteBufCodecs.BOOL, PacketSetTileFrequency::set,
          TypedIdentity.STREAM_CODEC, PacketSetTileFrequency::data,
          BlockPos.STREAM_CODEC, PacketSetTileFrequency::pos,
          PacketSetTileFrequency::new
    );

    public PacketSetTileFrequency(boolean set, FrequencyType<?> type, FrequencyIdentity data, BlockPos pos) {
        this(set, new TypedIdentity(type, data), pos);
    }

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketSetTileFrequency> type() {
        return TYPE;
    }

    @Override
    public void handle(Player player) {
        BlockEntity tile = PacketUtils.guiTile(player, pos);
        if (tile instanceof IFrequencyHandler frequencyHandler && IBlockSecurityUtils.INSTANCE.canAccess(player, player.level(), pos, tile)) {
            if (set) {
                frequencyHandler.setFrequency(data.type(), data.data(), player.getUUID());
            } else {
                frequencyHandler.removeFrequency(data.type(), data.data(), player.getUUID());
            }
        }
    }
}