package mekanism.common.network.to_server.qio;

import io.netty.buffer.ByteBuf;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * QIO item viewer held-stack placement payload (client to server).
 *
 * <p>Fabric adaptation of the official packet: codec and handling logic unchanged; NeoForge's {@code IMekanismPacket}/{@code IPayloadContext} dispatch is
 * replaced by the vanilla {@link CustomPacketPayload} contract with handling split into {@link #handle(Player)}. The server receiver lives in the Fabric
 * networking owner and runs on the server thread, matching the original main-thread semantics.</p>
 */
public record PacketQIOItemViewerSlotPlace(int count) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PacketQIOItemViewerSlotPlace> TYPE = new CustomPacketPayload.Type<>(
          ResourceLocation.fromNamespaceAndPath("mekanism", "qio_place"));
    public static final StreamCodec<ByteBuf, PacketQIOItemViewerSlotPlace> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
          PacketQIOItemViewerSlotPlace::new, PacketQIOItemViewerSlotPlace::count
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketQIOItemViewerSlotPlace> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (player.containerMenu instanceof QIOItemViewerContainer container) {
            QIOFrequency freq = container.getFrequency();
            if (freq != null) {
                ItemStack curStack = container.getCarried();
                //Count should always be greater than zero but validate against invalid packets
                if (!curStack.isEmpty() && count > 0) {
                    ItemStack toAdd;
                    if (count < curStack.getCount()) {//Only adding part of the stack
                        toAdd = curStack.copyWithCount(count);
                    } else {//Try to add the full held stack
                        toAdd = curStack;
                    }
                    ItemStack rejects = freq.addItem(toAdd);
                    //Calculate actual amount we were able to add of what we tried to add
                    int placed = toAdd.getCount() - rejects.getCount();
                    if (placed > 0) {
                        //If we added any from the held stack, shrink the held stack (which will cause it to be updated on the client)
                        curStack.shrink(placed);
                    }
                }
            }
        }
    }
}
