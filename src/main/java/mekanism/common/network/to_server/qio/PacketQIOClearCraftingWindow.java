package mekanism.common.network.to_server.qio;

import io.netty.buffer.ByteBuf;
import mekanism.common.content.qio.QIOCraftingWindow;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QIO crafting-window clear payload (client to server).
 *
 * <p>Fabric adaptation of the official packet: codec and handling logic unchanged; NeoForge's {@code IMekanismPacket}/{@code IPayloadContext} dispatch is
 * replaced by the vanilla {@link CustomPacketPayload} contract with handling split into {@link #handle(Player)}. The server receiver lives in the Fabric
 * networking owner and runs on the server thread, matching the original main-thread semantics.</p>
 */
public record PacketQIOClearCraftingWindow(byte window, boolean toPlayerInv) implements CustomPacketPayload {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism");

    public static final CustomPacketPayload.Type<PacketQIOClearCraftingWindow> TYPE = new CustomPacketPayload.Type<>(
          ResourceLocation.fromNamespaceAndPath("mekanism", "clear_qio"));
    public static final StreamCodec<ByteBuf, PacketQIOClearCraftingWindow> STREAM_CODEC = StreamCodec.composite(
          ByteBufCodecs.BYTE, PacketQIOClearCraftingWindow::window,
          ByteBufCodecs.BOOL, PacketQIOClearCraftingWindow::toPlayerInv,
          PacketQIOClearCraftingWindow::new
    );

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketQIOClearCraftingWindow> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (player.containerMenu instanceof QIOItemViewerContainer container) {
            byte selectedCraftingGrid = container.getSelectedCraftingGrid(player.getUUID());
            if (selectedCraftingGrid == -1) {
                LOGGER.warn("Received clear request from: {}, but they do not currently have a crafting window open.", player);
            } else if (selectedCraftingGrid != window) {
                LOGGER.warn("Received clear request from: {}, but they currently have a different crafting window open.", player);
            } else {
                QIOCraftingWindow craftingWindow = container.getCraftingWindow(selectedCraftingGrid);
                craftingWindow.emptyTo(toPlayerInv, container.getHotBarSlots(), container.getMainInventorySlots());
            }
        }
    }
}
