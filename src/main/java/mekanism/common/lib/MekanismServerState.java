package mekanism.common.lib;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import mekanism.api.MekanismServerLifecycle;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;

/**
 * Fabric port delta: real server-lifetime owner of Mekanism global game state that official Mekanism held as static fields on the excluded {@code mekanism.common.Mekanism}
 * entrypoint and cleared in its {@code serverStopped} listener.
 *
 * <p>Each field here is a complete responsibility move with its original lifetime semantics: initialized empty, mutated only by the domain that owns the mutations, and
 * cleared after saves in the server-stopped domain cleanup (before {@link MekanismServerLifecycle#releaseStoppedServer(MinecraftServer)} releases the shared server).
 * When the owning domain is admitted in a later batch, its mutators call these same methods rather than re-hosting the state.</p>
 */
public final class MekanismServerState {

    private MekanismServerState() {
    }

    /**
     * Global positions of chunks currently being vibrated by a Seismic Vibrator. Read by {@code WorldUtils#isChunkVibrated}; mutated by the seismic vibrator domain.
     */
    private static final Set<GlobalPos> activeVibrators = new ObjectOpenHashSet<>();

    public static Set<GlobalPos> activeVibrators() {
        return activeVibrators;
    }

    /** Clears server-scoped game state. Only called from the post-save server-stopped domain cleanup, for the server instance that is stopping. */
    public static void clearServerState() {
        activeVibrators.clear();
    }
}
