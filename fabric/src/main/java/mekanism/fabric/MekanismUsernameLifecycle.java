package mekanism.fabric;

import mekanism.common.UsernameCache;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
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

    private static void recordLogin(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        //JOIN is (handler, sender, server): the actual player comes from the handler
        UsernameCache.setLastKnownUsername(handler.player.getUUID(), handler.player.getGameProfile().getName());
    }
}
