package mekanism.common.network.to_client;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Native form of the original show_mode_change instanced payload: same identifier and empty wire body. */
public record PacketShowModeChange() implements CustomPacketPayload {
    public static final PacketShowModeChange INSTANCE = new PacketShowModeChange();
    public static final Type<PacketShowModeChange> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mekanism", "show_mode_change"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketShowModeChange> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<PacketShowModeChange> type() {
        return TYPE;
    }
}
