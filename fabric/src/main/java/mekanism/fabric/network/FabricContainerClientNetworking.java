package mekanism.fabric.network;

import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.network.to_client.PacketShowModeChange;
import mekanism.client.render.hud.MekanismStatusOverlay;
import mekanism.common.network.to_client.container.PacketUpdateContainer;
import mekanism.common.network.to_client.qio.PacketQIOItemViewerKill;
import mekanism.common.network.to_client.qio.PacketUpdateItemViewer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Physical-client receivers for the container/QIO S2C payloads (Task052 REVIEW_03 finding 5).
 *
 * <p>Each receiver resolves the player's currently open container on the client thread (Fabric dispatches play receivers inside the packet listener's client-owning
 * task, matching the original physical-client handler thread) and applies the payload only when the open menu is the expected container type — the same
 * window/container checks the official client packet handlers performed. The QIO viewer update/kill payloads additionally resolve the open
 * {@link QIOItemViewerContainer} explicitly, as the official handlers did.</p>
 */
public final class FabricContainerClientNetworking {

    public static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/ContainerNetwork");
    private static boolean initialized;

    private FabricContainerClientNetworking() {
    }

    public static synchronized void initializeClient() {
        if (initialized) {
            return;
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(PacketUpdateContainer.TYPE, FabricContainerClientNetworking::handleUpdateContainer)) {
            throw new IllegalStateException("A client receiver is already registered for " + PacketUpdateContainer.TYPE.id());
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(PacketUpdateItemViewer.TYPE, FabricContainerClientNetworking::handleUpdateItemViewer)) {
            throw new IllegalStateException("A client receiver is already registered for " + PacketUpdateItemViewer.TYPE.id());
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(PacketQIOItemViewerKill.TYPE, FabricContainerClientNetworking::handleKillItemViewer)) {
            throw new IllegalStateException("A client receiver is already registered for " + PacketQIOItemViewerKill.TYPE.id());
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(PacketShowModeChange.TYPE, (payload, context) -> MekanismStatusOverlay.INSTANCE.setTimer())) {
            throw new IllegalStateException("A client receiver is already registered for " + PacketShowModeChange.TYPE.id());
        }
        initialized = true;
    }

    private static void handleUpdateContainer(PacketUpdateContainer payload, ClientPlayNetworking.Context context) {
        LocalPlayer player = context.player();
        if (player.containerMenu instanceof mekanism.common.inventory.container.MekanismContainer container) {
            payload.handle(container);
        }
    }

    private static void handleUpdateItemViewer(PacketUpdateItemViewer payload, ClientPlayNetworking.Context context) {
        LocalPlayer player = context.player();
        if (player.containerMenu instanceof QIOItemViewerContainer container) {
            payload.handle(container);
        }
    }

    private static void handleKillItemViewer(PacketQIOItemViewerKill payload, ClientPlayNetworking.Context context) {
        LocalPlayer player = context.player();
        if (player.containerMenu instanceof QIOItemViewerContainer container) {
            payload.handle(container);
        }
    }
}
