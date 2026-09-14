package mekanism.common.network.to_client.qio;

import mekanism.common.inventory.container.QIOItemViewerContainer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Kill QIO item viewer payload (server to client, no data).
 *
 * <p>Fabric adaptation of the official {@code PacketHandler#killItemViewer} instanced payload ({@code mekanism:kill_qio}): the identifier and empty body are
 * unchanged; the payload is a named record instead of an anonymous registrar instance, and handling is split into {@link #handle(QIOItemViewerContainer)}. The
 * physical-client receiver lives in the Fabric networking owner.</p>
 */
public record PacketQIOItemViewerKill() implements CustomPacketPayload {

    public static final PacketQIOItemViewerKill INSTANCE = new PacketQIOItemViewerKill();

    public static final CustomPacketPayload.Type<PacketQIOItemViewerKill> TYPE = new CustomPacketPayload.Type<>(
          ResourceLocation.fromNamespaceAndPath("mekanism", "kill_qio"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketQIOItemViewerKill> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketQIOItemViewerKill> type() {
        return TYPE;
    }

    public void handle(QIOItemViewerContainer container) {
        container.handleKill();
    }
}
