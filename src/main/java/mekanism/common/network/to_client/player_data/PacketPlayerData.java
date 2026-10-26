package mekanism.common.network.to_client.player_data;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import mekanism.common.base.PlayerState;
import mekanism.common.network.IMekanismPacket;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public record PacketPlayerData(UUID uuid, boolean activeJetpack, boolean activeScubaMask, boolean activeModulator) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketPlayerData> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "player_data"));
    public static final StreamCodec<ByteBuf, PacketPlayerData> STREAM_CODEC = StreamCodec.composite(
          UUIDUtil.STREAM_CODEC, PacketPlayerData::uuid,
          ByteBufCodecs.BOOL, PacketPlayerData::activeJetpack,
          ByteBufCodecs.BOOL, PacketPlayerData::activeScubaMask,
          ByteBufCodecs.BOOL, PacketPlayerData::activeModulator,
          PacketPlayerData::new
    );

    public PacketPlayerData(UUID uuid) {
        this(uuid,
              PlayerState.INSTANCE.isJetpackOn(uuid),
              PlayerState.INSTANCE.isScubaMaskOn(uuid),
              PlayerState.INSTANCE.isGravitationalModulationOn(uuid)
        );
    }

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketPlayerData> type() {
        return TYPE;
    }

    @Override
    public void handle(Player player) {
        PlayerState.INSTANCE.setJetpackState(uuid, activeJetpack, false);
        PlayerState.INSTANCE.setScubaMaskState(uuid, activeScubaMask, false);
        PlayerState.INSTANCE.setGravitationalModulationState(uuid, activeModulator, false);
    }
}