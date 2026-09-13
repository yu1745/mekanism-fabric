package mekanism.fabric.network;

import mekanism.common.network.to_client.PacketUpdateTile;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Physical-client receiver for {@link PacketUpdateTile}.
 *
 * <p>Fabric adaptation of the official payload handler: the vanilla block-entity packet path reaches reduced updates through the mixin dispatch hook, while this custom
 * payload reaches them through Fabric's play S2C receiver. Both resolve the tile through the <em>current</em> connection and world — no world or registry access is
 * retained from registration time — and the reduced load runs on the client owner thread because Fabric dispatch happens inside the packet listener's own task. An
 * unloaded position is skipped and a missing or wrong-class tile keeps the official warning.</p>
 */
public final class FabricTileUpdateHandler {

    public static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/TileUpdate");
    private static boolean initialized;

    private FabricTileUpdateHandler() {
    }

    public static synchronized void initializeClient() {
        if (initialized) {
            return;
        }
        if (!ClientPlayNetworking.registerGlobalReceiver(PacketUpdateTile.TYPE, FabricTileUpdateHandler::handle)) {
            throw new IllegalStateException("A client receiver is already registered for " + PacketUpdateTile.TYPE.id());
        }
        initialized = true;
    }

    private static void handle(PacketUpdateTile payload, ClientPlayNetworking.Context context) {
        LocalPlayer player = context.player();
        ClientLevel level = player.level() instanceof ClientLevel clientLevel ? clientLevel : null;
        if (level == null) {
            //The player is between worlds; the current owner world is absent, so nothing is applied rather than applying to a stale world.
            return;
        }
        //The current client level carries the registry access frozen for this connection, so no access is retained across a reconnect or dimension change.
        payload.handle(level, level.registryAccess());
    }
}
