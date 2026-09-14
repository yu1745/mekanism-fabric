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
 * <p>Each field here is a complete responsibility move with its original lifetime semantics: initialized empty, mutated by the domains that own the mutations
 * (reached through the original excluded static-field aliases where those still exist), and cleared after saves in the server-stopped domain cleanup (before
 * {@link MekanismServerLifecycle#releaseStoppedServer(MinecraftServer)} releases the shared server).</p>
 */
public final class MekanismServerState {

    private MekanismServerState() {
    }

    /**
     * Global positions of chunks currently being vibrated by a Seismic Vibrator. Read by {@code WorldUtils#isChunkVibrated}. The excluded original
     * {@code Mekanism.activeVibrators} field aliases this exact set now, so the original mutators reach the owner set through their existing references:
     * {@code TileEntitySeismicVibrator} add/remove, {@code Mekanism.serverStopped} clear, and {@code MekanismClient.resetDimensionChange} clear.
     */
    private static final Set<GlobalPos> activeVibrators = new ObjectOpenHashSet<>();

    public static Set<GlobalPos> activeVibrators() {
        return activeVibrators;
    }

    /** Clears server-scoped game state. Called from the post-save server-stopped domain cleanup only for the server instance that is stopping (same-server identity guarded by the bootstrap). */
    public static void clearServerState() {
        activeVibrators.clear();
        mekanism.common.lib.frequency.FrequencyType.clear();
        mekanism.common.content.qio.QIOGlobalItemLookup.INSTANCE.reset();
        mekanism.common.lib.radiation.PlayerExposure.clear();
        mekanism.common.lib.multiblock.MultiblockManager.reset();
        mekanism.common.lib.frequency.FrequencyManager.reset();
        mekanism.common.content.transporter.TransporterManager.reset();
        mekanism.common.content.transporter.PathfinderCache.reset();
        mekanism.common.lib.transmitter.TransmitterNetworkRegistry.reset();
    }
}
