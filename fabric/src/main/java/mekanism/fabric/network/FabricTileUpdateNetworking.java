package mekanism.fabric.network;

import java.util.Collection;
import mekanism.common.network.to_client.PacketUpdateTile;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Server-side recipient resolution for the Mekanism reduced-tile update payload.
 *
 * <p>This is the Fabric adaptation of NeoForge's {@code PacketDistributor.sendToPlayersTrackingChunk} plus {@code PacketUtils.hasPlayersTracking}: the tracking tile
 * chooses the recipients through Fabric's {@link PlayerLookup}, and the caller decides whether a payload is built at all. Tracking a block entity and tracking its
 * position are equivalent in vanilla's chunk tracking, and going through the position keeps the reduced-update call sites level-based rather than tile-based.</p>
 *
 * <p>Only a loaded {@link ServerLevel} is accepted; any other level yields no recipients, which preserves the official behaviour where a client-side or unloaded
 * context sends nothing.</p>
 */
public final class FabricTileUpdateNetworking {

    private static boolean initialized;

    private FabricTileUpdateNetworking() {
    }

    /**
     * Registers the reduced-tile update payload in the play S2C direction. Called from the dedicated-server-safe common bootstrap, so the registration is present on
     * both a physical server and the integrated server, while the client receiver stays in {@link FabricTileUpdateHandler}.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        PayloadTypeRegistry.playS2C().register(PacketUpdateTile.TYPE, PacketUpdateTile.STREAM_CODEC);
        initialized = true;
    }

    /**
     * Players currently tracking the chunk containing {@code pos}.
     */
    public static Collection<ServerPlayer> tracking(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            return PlayerLookup.tracking(serverLevel, pos);
        }
        return java.util.List.of();
    }

    /**
     * Sends the exact reduced-update payload to one tracking player.
     */
    public static void send(ServerPlayer player, PacketUpdateTile payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
