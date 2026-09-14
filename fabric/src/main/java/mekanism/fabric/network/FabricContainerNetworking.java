package mekanism.fabric.network;

import mekanism.common.network.to_client.container.PacketUpdateContainer;
import mekanism.common.network.to_client.PacketShowModeChange;
import mekanism.common.network.to_client.qio.PacketQIOItemViewerKill;
import mekanism.common.network.to_client.qio.PacketUpdateItemViewer;
import mekanism.common.network.to_server.PacketWindowSelect;
import mekanism.common.network.to_server.PacketItemGuiInteract;
import mekanism.common.network.to_server.qio.PacketQIOClearCraftingWindow;
import mekanism.common.network.to_server.qio.PacketQIOFillCraftingWindow;
import mekanism.common.network.to_server.qio.PacketQIOItemViewerSlotPlace;
import mekanism.common.network.to_server.qio.PacketQIOItemViewerSlotShiftTake;
import mekanism.common.network.to_server.qio.PacketQIOItemViewerSlotTake;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric networking owner for the container/QIO play payloads (Task052 REVIEW_03 finding 5).
 *
 * <p>Registers the payload codecs in their real directions and the server-side receivers for every client-to-server container/QIO payload. Fabric's play receiver
 * contract executes handlers on the server main thread, matching the NeoForge {@code IPayloadContext} main-thread semantics of the original dispatch; each payload's
 * official {@code handle} method performs the original open-container type check (for example {@code PacketWindowSelect#handle} requires the player's open menu to be
 * a {@code MekanismContainer}), so the receiver is a plain main-thread dispatch and the container checks are preserved unchanged.</p>
 *
 * <p>The physical-client S2C receivers live in {@link FabricContainerClientNetworking}. This owner is part of the failing Task052 WIP work-list: it registers the
 * same container/QIO sources that are being integrated, and until that closure compiles this registration is source-complete but not yet shipped.</p>
 */
public final class FabricContainerNetworking {

    private static boolean initialized;

    private FabricContainerNetworking() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        //S2C container sync payloads (client receivers in FabricContainerClientNetworking)
        PayloadTypeRegistry.playS2C().register(PacketUpdateContainer.TYPE, PacketUpdateContainer.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketUpdateItemViewer.TYPE, PacketUpdateItemViewer.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketQIOItemViewerKill.TYPE, PacketQIOItemViewerKill.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PacketShowModeChange.TYPE, PacketShowModeChange.STREAM_CODEC);
        //C2S container/QIO interaction payloads (server receivers below)
        PayloadTypeRegistry.playC2S().register(PacketWindowSelect.TYPE, PacketWindowSelect.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketItemGuiInteract.TYPE, PacketItemGuiInteract.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketQIOClearCraftingWindow.TYPE, PacketQIOClearCraftingWindow.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketQIOFillCraftingWindow.TYPE, PacketQIOFillCraftingWindow.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketQIOItemViewerSlotPlace.TYPE, PacketQIOItemViewerSlotPlace.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketQIOItemViewerSlotShiftTake.TYPE, PacketQIOItemViewerSlotShiftTake.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PacketQIOItemViewerSlotTake.TYPE, PacketQIOItemViewerSlotTake.STREAM_CODEC);
        dispatch(PacketWindowSelect.TYPE, PacketWindowSelect::handle);
        dispatch(PacketItemGuiInteract.TYPE, PacketItemGuiInteract::handle);
        dispatch(PacketQIOClearCraftingWindow.TYPE, PacketQIOClearCraftingWindow::handle);
        dispatch(PacketQIOFillCraftingWindow.TYPE, PacketQIOFillCraftingWindow::handle);
        dispatch(PacketQIOItemViewerSlotPlace.TYPE, PacketQIOItemViewerSlotPlace::handle);
        dispatch(PacketQIOItemViewerSlotShiftTake.TYPE, PacketQIOItemViewerSlotShiftTake::handle);
        dispatch(PacketQIOItemViewerSlotTake.TYPE, PacketQIOItemViewerSlotTake::handle);
        initialized = true;
    }

    private static <MSG extends CustomPacketPayload> void dispatch(CustomPacketPayload.Type<MSG> type, ServerPayloadHandler<MSG> handler) {
        if (!ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.handle(payload, context.player()))) {
            throw new IllegalStateException("A server receiver is already registered for " + type.id());
        }
    }

    @FunctionalInterface
    private interface ServerPayloadHandler<MSG> {

        void handle(MSG payload, ServerPlayer player);
    }
}
