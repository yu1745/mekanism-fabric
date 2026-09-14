package mekanism.common.network.to_client.container;

import java.util.List;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.network.to_client.container.property.PropertyData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Container property sync payload.
 *
 * <p>Fabric adaptation of the official packet (following the accepted PacketUpdateTile pattern): the identifier, window id codec and property list codec are
 * unchanged; NeoForge's {@code IMekanismPacket}/{@code IPayloadContext} dispatch is replaced by the vanilla {@link CustomPacketPayload} contract, and handling is
 * split into {@link #handle(MekanismContainer)} so the record stays a plain payload. The physical-client receiver lives in the Fabric networking owner and is
 * responsible for resolving the player's open container on the client thread.</p>
 */
public record PacketUpdateContainer(short windowId, List<PropertyData> data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PacketUpdateContainer> TYPE = new CustomPacketPayload.Type<>(
          ResourceLocation.fromNamespaceAndPath("mekanism", "update_container"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketUpdateContainer> STREAM_CODEC = StreamCodec.composite(
          ByteBufCodecs.SHORT, PacketUpdateContainer::windowId,
          PropertyData.GENERIC_STREAM_CODEC.apply(ByteBufCodecs.list()), PacketUpdateContainer::data,
          PacketUpdateContainer::new
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketUpdateContainer> type() {
        return TYPE;
    }

    public void handle(MekanismContainer container) {
        //Ensure that the container is one of ours, and the window id is the same as we expect it to be
        if (container.containerId == windowId) {
            // and if so handle the packet
            for (PropertyData datum : data) {
                datum.handleWindowProperty(container);
            }
        }
    }
}
