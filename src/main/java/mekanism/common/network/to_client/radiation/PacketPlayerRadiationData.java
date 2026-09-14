package mekanism.common.network.to_client.radiation;

import io.netty.buffer.ByteBuf;
import mekanism.fabric.radiation.FabricRadiationAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public record PacketPlayerRadiationData(double radiation) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PacketPlayerRadiationData> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "player_radiation"));
    public static final StreamCodec<ByteBuf, PacketPlayerRadiationData> STREAM_CODEC = ByteBufCodecs.DOUBLE.map(
          PacketPlayerRadiationData::new, PacketPlayerRadiationData::radiation
    );

    public PacketPlayerRadiationData(Player player) {
        this(FabricRadiationAttachments.getRadiation(player));
    }

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketPlayerRadiationData> type() {
        return TYPE;
    }

    public void handle(Player player) {
        FabricRadiationAttachments.setRadiation(player, radiation);
    }
}
