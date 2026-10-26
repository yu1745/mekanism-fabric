package mekanism.common.network.to_server;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import mekanism.common.base.KeySync;
import mekanism.common.network.IMekanismPacket;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record PacketKey(int key, boolean add) implements IMekanismPacket {

    public static final CustomPacketPayload.Type<PacketKey> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "key"));
    public static final StreamCodec<ByteBuf, PacketKey> STREAM_CODEC = StreamCodec.composite(
          ByteBufCodecs.VAR_INT, PacketKey::key,
          ByteBufCodecs.BOOL, PacketKey::add,
          PacketKey::new
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketKey> type() {
        return TYPE;
    }

    @Override
    public void handle(Player player) {
        UUID playerId = player.getUUID();
        if (add) {
            KeySync.INSTANCE.add(playerId, key);
        } else {
            KeySync.INSTANCE.remove(playerId, key);
        }
    }
}