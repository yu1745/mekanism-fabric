package mekanism.common.network.to_client.qio;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.lib.inventory.HashedItem.UUIDAwareHashedItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Incremental QIO item viewer update payload (server to client).
 *
 * <p>Fabric adaptation of the official packet: codec unchanged; NeoForge's {@code IMekanismPacket}/{@code IPayloadContext} dispatch is replaced by the vanilla
 * {@link CustomPacketPayload} contract with handling split into {@link #handle(QIOItemViewerContainer)}. The physical-client receiver lives in the Fabric
 * networking owner and resolves the player's open container on the client thread.</p>
 */
public class PacketUpdateItemViewer implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PacketUpdateItemViewer> TYPE = new CustomPacketPayload.Type<>(
          ResourceLocation.fromNamespaceAndPath("mekanism", "update_qio"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Object2LongMap<UUIDAwareHashedItem>> ITEM_MAP_CODEC = ByteBufCodecs.map(Object2LongOpenHashMap::new,
          UUIDAwareHashedItem.STREAM_CODEC, ByteBufCodecs.VAR_LONG
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketUpdateItemViewer> STREAM_CODEC = StreamCodec.composite(
          ByteBufCodecs.VAR_LONG, pkt -> pkt.countCapacity,
          ByteBufCodecs.VAR_INT, pkt -> pkt.typeCapacity,
          ITEM_MAP_CODEC, pkt -> pkt.itemMap,
          PacketUpdateItemViewer::new
    );

    private final Object2LongMap<UUIDAwareHashedItem> itemMap;
    private final long countCapacity;
    private final int typeCapacity;

    public PacketUpdateItemViewer(long countCapacity, int typeCapacity, Object2LongMap<UUIDAwareHashedItem> itemMap) {
        this.itemMap = itemMap;
        this.countCapacity = countCapacity;
        this.typeCapacity = typeCapacity;
    }

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketUpdateItemViewer> type() {
        return TYPE;
    }

    public void handle(QIOItemViewerContainer container) {
        container.handleUpdate(itemMap, countCapacity, typeCapacity);
    }
}
