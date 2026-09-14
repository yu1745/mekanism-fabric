package mekanism.common.lib.transmitter.acceptor;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import mekanism.common.content.network.transmitter.MechanicalPipe;
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.WorldUtils;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Fluid-only derived {@link NetworkAcceptorCache} specialization (Task052 continuation08 ownership boundary).
 *
 * <p>All public readers ({@link #getAcceptorValues()}, {@link #getAcceptorCount()}, {@link #hasAcceptor(BlockPos)},
 * {@link #getCachedAcceptor(long, Direction)}, {@link #getAcceptorDirections(long)} and
 * {@link #getAcceptorFastIterator()}) build a detached ephemeral position-&gt;face-&gt;{@link Storage} snapshot from the
 * <b>current</b> valid, loaded member pipes of the authoritative FluidNetwork membership supplier. The base retained
 * {@code ACCEPTOR} map is never populated, so no external Fabric {@code Storage} is retained across ticks, copied on
 * adopt, or reused next tick. Provider results are re-read on every public read (no per-tick memoization), so
 * formerly {@code null} providers are rediscovered.
 *
 * <p>Discovery checks all six sides of each member pipe, running the official {@code refreshAcceptorConnections}
 * bitmap policy (packet only when that side's bit changes) before the {@code canConnectToAcceptor}/
 * {@code getAcceptor} decision, preserving redstone/mutual-transmission exclusions and opposite-face keying. Remote
 * or no-world queries see no member pipes and return empty results without any server lookup or chunk loading.
 *
 * <p>Lifecycle handling honors the derived-state design: connection refresh (commit of pending dirty sides) and
 * pending-dirty propagation on adopt remain real, while there is no retained endpoint state to transfer on merges or
 * removals - current authoritative membership is simply re-read after merges/removals. No {@code Storage} is copied
 * on adopt (base retained-map adoption is explicitly overridden out).
 *
 * <p>Accepted, unbenchmarked cost (parent decision): each snapshot read is O(6 * current member pipes) plus a fresh
 * relevant PULL-side lookup. This is correctness-first, not performance parity.
 */
public class FluidNetworkAcceptorCache extends NetworkAcceptorCache<Storage<FluidVariant>> {

    private final Supplier<Collection<MechanicalPipe>> membership;

    /**
     * @param membership Deferred supplier of the authoritative FluidNetwork membership (e.g.
     *                   {@code DynamicNetwork#getTransmitters()}). Evaluated lazily on each public read only, never
     *                   during network construction, so no subclass state is touched at build time.
     */
    public FluidNetworkAcceptorCache(Supplier<Collection<MechanicalPipe>> membership) {
        this.membership = membership;
    }

    /**
     * Builds a detached ephemeral snapshot from current valid, loaded member pipes. Mirrors the official
     * {@code NetworkAcceptorCache#updateTransmitterOnSide} decision (refresh bitmap, then canSendTo/getAcceptor) but
     * checks all six sides instead of only previously positive connections and stores the result only in the returned
     * throwaway map, never in the base retained map.
     */
    private Long2ObjectMap<Map<Direction, Storage<FluidVariant>>> buildSnapshot() {
        Long2ObjectMap<Map<Direction, Storage<FluidVariant>>> snapshot = new Long2ObjectOpenHashMap<>();
        for (MechanicalPipe pipe : membership.get()) {
            //Client networks and unloaded/removed transmitter tiles contribute nothing; never load chunks for a read
            if (pipe == null || pipe.isRemote() || !pipe.isValid()) {
                continue;
            }
            for (Direction side : EnumUtils.DIRECTIONS) {
                //Official bitmap policy: refresh this side's acceptor-connection validity (and packet on change)
                pipe.refreshAcceptorConnections(side);
                //canSendTo decision before the acceptor lookup, exactly like updateTransmitterOnSide
                if (!pipe.canConnectToAcceptor(side)) {
                    continue;
                }
                //Bitmap-gated fresh per-pipe lookup (FluidAcceptorCache); null results (including formerly null
                // providers that are still not valid) are simply not part of this snapshot
                Storage<FluidVariant> acceptor = pipe.getAcceptor(side);
                if (acceptor == null) {
                    continue;
                }
                long acceptorPos = WorldUtils.relativePos(pipe.getWorldPositionLong(), side);
                snapshot.computeIfAbsent(acceptorPos, pos -> new EnumMap<>(Direction.class)).put(side.getOpposite(), acceptor);
            }
        }
        return snapshot;
    }

    @Override
    public void updateTransmitterOnSide(Transmitter<Storage<FluidVariant>, ?, ?> transmitter, Direction side) {
        //Derived state: keep the real official connection bitmap refresh (and change packet), but do NOT retain the
        // returned Storage in the base map. Public readers re-derive it freshly on each read.
        transmitter.refreshAcceptorConnections(side);
    }

    @Override
    public void adoptAcceptors(NetworkAcceptorCache<Storage<FluidVariant>> other) {
        //No retained endpoint state exists to transfer: the base retained-map copy is deliberately skipped. Only the
        // pending dirty sides propagate so post-merge commit still refreshes connection bitmaps; the merged
        // membership is re-read authoritatively by the next public read.
        adoptPendingDirty(other);
    }

    /**
     * @apiNote Listeners should not be added to these storages here as they may not correspond to an actual handler and may not get invalidated.
     */
    @Override
    public ObjectIterator<Long2ObjectMap.Entry<Map<Direction, Storage<FluidVariant>>>> getAcceptorFastIterator() {
        return Long2ObjectMaps.fastIterator(buildSnapshot());
    }

    /**
     * @apiNote Listeners should not be added to these storages here as they may not correspond to an actual handler and may not get invalidated.
     */
    @Override
    public Collection<Map<Direction, Storage<FluidVariant>>> getAcceptorValues() {
        return Collections.unmodifiableCollection(buildSnapshot().values());
    }

    @Override
    public int getAcceptorCount() {
        //Count multiple connections to the same position as multiple acceptors
        int count = 0;
        for (Map<Direction, Storage<FluidVariant>> faces : buildSnapshot().values()) {
            count += faces.size();
        }
        return count;
    }

    @Override
    public boolean hasAcceptor(BlockPos acceptorPos) {
        return buildSnapshot().containsKey(acceptorPos.asLong());
    }

    @Nullable
    @Override
    public Storage<FluidVariant> getCachedAcceptor(long acceptorPos, Direction side) {
        return buildSnapshot().getOrDefault(acceptorPos, Collections.emptyMap()).get(side);
    }

    @Override
    public Set<Direction> getAcceptorDirections(long pos) {
        //Detached copy (empty when absent), never an invalidated view of ephemeral state
        Map<Direction, Storage<FluidVariant>> faces = buildSnapshot().get(pos);
        return faces == null ? Collections.emptySet() : EnumSet.copyOf(faces.keySet());
    }
}
