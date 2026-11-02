package mekanism.common.util;

/**
 * Fabric port delta: replaces the NeoForge FML utility class named {@code EffectiveSide} (its {@code fml.util.thread} package) for deciding which logical
 * side the current thread belongs to.
 *
 * <p>Upstream answers SERVER exactly when the current thread's {@link ThreadGroup} carries the sided server marker; the platform mixin in this project
 * installs that marker on the real {@code MinecraftServer.spin} thread-creation entry, matching NeoForge 1.21.1's {@code SidedThreadGroups.SERVER}
 * spin patch. Threads spawned by the server thread — including the server's Netty IO workers, whose group factory is first built on the server thread
 * inside {@code ServerConnectionListener} — inherit the marker through ordinary thread-group membership. Every other group (client, client Netty,
 * bootstrap/worker) answers CLIENT, exactly like FML's default for non-sided groups.</p>
 */
public final class EffectiveSide {

    /**
     * Marker group, created lazily by the platform mixin on the thread that launches the server so its parent is that launcher's group (client render
     * thread for an integrated server, launcher main thread for a dedicated server). Created once per JVM and reused across server restarts, mirroring
     * FML's static {@code SidedThreadGroups.SERVER}.
     */
    private static volatile ThreadGroup serverThreadGroup;

    private EffectiveSide() {
    }

    /**
     * Platform seam: returns (creating it on first use) the group every server thread must be constructed in. Only the {@code MinecraftServer.spin}
     * mixin calls this.
     */
    public static ThreadGroup serverThreadGroup() {
        ThreadGroup group = serverThreadGroup;
        if (group == null) {
            group = new ThreadGroup("mekanism-server");
            serverThreadGroup = group;
        }
        return group;
    }

    public static boolean isServer() {
        return Thread.currentThread().getThreadGroup() == serverThreadGroup;
    }

    public static boolean isClient() {
        return !isServer();
    }
}
