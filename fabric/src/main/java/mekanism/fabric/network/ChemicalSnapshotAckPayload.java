package mekanism.fabric.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Successful client publication acknowledgement for an initial chemical snapshot. */
public record ChemicalSnapshotAckPayload(String sessionId, long epoch) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_snapshot_ack");
    public static final Type<ChemicalSnapshotAckPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, ChemicalSnapshotAckPayload> CODEC = StreamCodec.composite(
          ByteBufCodecs.stringUtf8(64), ChemicalSnapshotAckPayload::sessionId,
          ByteBufCodecs.VAR_LONG, ChemicalSnapshotAckPayload::epoch,
          ChemicalSnapshotAckPayload::new);

    public ChemicalSnapshotAckPayload {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 64 || epoch <= 0) {
            throw new IllegalArgumentException("Invalid chemical snapshot acknowledgement");
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
