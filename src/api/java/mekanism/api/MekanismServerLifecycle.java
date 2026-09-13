package mekanism.api;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port delta: real lifecycle-bound owner of the currently running Mekanism-aware {@link MinecraftServer}, replacing the NeoForge
 * {@code ServerLifecycleHooks.getCurrentServer()} global that official Mekanism consulted from API back-compat helpers and {@code MekanismSavedData}.
 *
 * <p>The server is captured by the Fabric platform bootstrap on {@code ServerLifecycleEvents.SERVER_STARTING} — before any world exists, so overworld data
 * storage must not be touched from the capture callback — and released on {@code ServerLifecycleEvents.SERVER_STOPPED}, which Fabric fires after
 * {@code stopServer()} has completed (that is, after all saves). Release only happens for the exact same server instance, so an integrated-server restart
 * (stop old server, capture a new one) never observes a stale reference. Domain cleanup that needs the live server (frequency managers, saved data) runs
 * before the reference is released within the same stop callback.</p>
 *
 * @since 10.7.0 (Fabric port)
 */
@Internal
public final class MekanismServerLifecycle {

    private MekanismServerLifecycle() {
    }

    @Nullable
    private static MinecraftServer server;

    /**
     * {@return the currently running server, or {@code null} when no server is running} Dedicated and integrated servers are both tracked; on a client that
     * is not hosting an integrated server this returns {@code null} rather than guessing from the game instance.
     */
    @Nullable
    public static MinecraftServer getServer() {
        return server;
    }

    /** Captures the server that is starting. Called by the Fabric platform bootstrap only. */
    @Internal
    public static void captureStartingServer(MinecraftServer starting) {
        server = starting;
    }

    /**
     * Releases the reference to the stopped server. Called by the Fabric platform bootstrap only, after server saves and Mekanism domain cleanup, and only
     * when the stopped instance is still the captured one.
     */
    @Internal
    public static void releaseStoppedServer(MinecraftServer stopped) {
        if (server == stopped) {
            server = null;
        }
    }
}
