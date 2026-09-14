package mekanism.common.network.to_client.security;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import mekanism.common.lib.security.ClientSecurityData;
import net.minecraft.resources.ResourceLocation;
import mekanism.common.lib.security.SecurityData;
import mekanism.common.lib.security.SecurityFrequency;
import mekanism.common.network.PacketUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PacketSyncSecurity(UUID playerUUID, String playerUsername, @Nullable SecurityData securityData) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PacketSyncSecurity> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "sync_security"));
    public static final StreamCodec<ByteBuf, PacketSyncSecurity> STREAM_CODEC = StreamCodec.composite(
          UUIDUtil.STREAM_CODEC, PacketSyncSecurity::playerUUID,
          ByteBufCodecs.stringUtf8(PacketUtils.LAST_USERNAME_LENGTH), PacketSyncSecurity::playerUsername,
          ByteBufCodecs.optional(SecurityData.STREAM_CODEC), packet -> Optional.ofNullable(packet.securityData),
          (uuid, name, data) -> new PacketSyncSecurity(uuid, name, data.orElse(null))
    );

    public PacketSyncSecurity(SecurityFrequency frequency) {
        this(frequency.getOwner(), frequency.getOwnerName(), new SecurityData(frequency));
    }

    public PacketSyncSecurity(UUID uuid) {
        this(uuid, MekanismUtils.getLastKnownUsername(uuid), null);
    }

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketSyncSecurity> type() {
        return TYPE;
    }

    public void handle() {
        ClientSecurityData.usernameMap().put(playerUUID, playerUsername);
        if (securityData != null) {
            ClientSecurityData.securityMap().put(playerUUID, securityData);
        }
    }
}