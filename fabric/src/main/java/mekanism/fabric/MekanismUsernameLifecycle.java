package mekanism.fabric;

import mekanism.common.UsernameCache;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Task052 real population wiring for {@link UsernameCache}: the original NeoForge {@code UsernameCache} is filled when a player logs in, and
 * {@code Frequency}/{@code MekanismUtils.getLastKnownUsername} then read the persisted last-known names for owner display. This owner registers the
 * Fabric play-join lifecycle hook that performs that population on the logical server (dedicated or integrated), on the server thread; the save-on-change
 * and JSON persistence semantics live in {@code UsernameCache} itself.
 */
public final class MekanismUsernameLifecycle {

    private MekanismUsernameLifecycle() {
    }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register(MekanismUsernameLifecycle::recordLogin);
    }

    private static void recordLogin(ServerGamePacketListenerImpl handler, net.minecraft.server.MinecraftServer server,
          net.minecraft.world.entity.player.Player player) {
        //JOIN fires on the server thread once the player is in the world; the game profile name is the authoritative current name
        UsernameCache.setLastKnownUsername(player.getUUID(), player.getGameProfile().getName());
    }
}
