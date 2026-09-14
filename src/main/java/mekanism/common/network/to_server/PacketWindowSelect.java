package mekanism.common.network.to_server;

import io.netty.buffer.ByteBuf;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Selected-window sync payload (client to server).
 *
 * <p>Fabric adaptation of the official packet: codec, window-type dispatch and cached no-window constant are unchanged; NeoForge's
 * {@code IMekanismPacket}/{@code IPayloadContext} dispatch is replaced by the vanilla {@link CustomPacketPayload} contract with the handling split into
 * {@link #handle(Player)}. The server receiver lives in the Fabric networking owner and runs on the server thread, matching the original main-thread
 * semantics.</p>
 */
@NothingNullByDefault
public record PacketWindowSelect(@Nullable SelectedWindowData selectedWindow) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PacketWindowSelect> TYPE = new CustomPacketPayload.Type<>(
          ResourceLocation.fromNamespaceAndPath("mekanism", "window_select"));

    private static final PacketWindowSelect NO_WINDOW = new PacketWindowSelect(null);
    private static final StreamCodec<ByteBuf, PacketWindowSelect> NO_WINDOW_STREAM_CODEC = StreamCodec.unit(NO_WINDOW);

    public static final StreamCodec<ByteBuf, PacketWindowSelect> STREAM_CODEC = ByteBufCodecs.BYTE.dispatch(
          packet -> packet.selectedWindow == null ? -1 : packet.selectedWindow.extraData, extraData -> {
        if (extraData == -1) {
            return NO_WINDOW_STREAM_CODEC;
        }
        return WindowType.STREAM_CODEC.map(
              windowType -> new PacketWindowSelect(windowType == WindowType.UNSPECIFIED ? SelectedWindowData.UNSPECIFIED : new SelectedWindowData(windowType, extraData)),
              //Note: We know the selected window is not null here
              packet -> packet.selectedWindow.type
        );
    });

    @NotNull
    @Override
    public CustomPacketPayload.Type<PacketWindowSelect> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (player.containerMenu instanceof MekanismContainer container) {
            container.setSelectedWindow(player.getUUID(), selectedWindow);
        }
    }
}
