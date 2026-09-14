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
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Native lookup endpoints are operation-local, not invalidation-aware capabilities. Every public read derives a
 * detached position/face snapshot from the real network's current membership, refreshing all six connection bits
 * before resolving providers. Nothing is retained or copied on merge except the original pending dirty sides.
 *
 * <p>This is the existing fluid cache algorithm shared with the chemical and heat networks. Membership is deferred
 * during superclass construction. Cost remains O(6 * current members) per read; no performance parity is claimed.
 */
public class FreshNetworkAcceptorCache<ACCEPTOR, TRANSMITTER extends Transmitter<ACCEPTOR, ?, ?>> extends NetworkAcceptorCache<ACCEPTOR> {

    private final Supplier<Collection<TRANSMITTER>> membership;

    public FreshNetworkAcceptorCache(Supplier<Collection<TRANSMITTER>> membership) {
        this.membership = membership;
    }

    private Long2ObjectMap<Map<Direction, ACCEPTOR>> buildSnapshot() {
        Long2ObjectMap<Map<Direction, ACCEPTOR>> snapshot = new Long2ObjectOpenHashMap<>();
        for (TRANSMITTER transmitter : membership.get()) {
            if (transmitter == null || transmitter.isRemote() || !transmitter.isValid()) {
                continue;
            }
            for (Direction side : EnumUtils.DIRECTIONS) {
                transmitter.refreshAcceptorConnections(side);
                if (!transmitter.canConnectToAcceptor(side)) {
                    continue;
                }
                ACCEPTOR acceptor = transmitter.getAcceptor(side);
                if (acceptor != null) {
                    long acceptorPos = WorldUtils.relativePos(transmitter.getWorldPositionLong(), side);
                    snapshot.computeIfAbsent(acceptorPos, pos -> new EnumMap<>(Direction.class)).put(side.getOpposite(), acceptor);
                }
            }
        }
        return snapshot;
    }

    @Override
    public void updateTransmitterOnSide(Transmitter<ACCEPTOR, ?, ?> transmitter, Direction side) {
        transmitter.refreshAcceptorConnections(side);
    }

    @Override
    public void adoptAcceptors(NetworkAcceptorCache<ACCEPTOR> other) {
        adoptPendingDirty(other);
    }

    @Override
    public ObjectIterator<Long2ObjectMap.Entry<Map<Direction, ACCEPTOR>>> getAcceptorFastIterator() {
        return Long2ObjectMaps.fastIterator(buildSnapshot());
    }

    @Override
    public Collection<Map<Direction, ACCEPTOR>> getAcceptorValues() {
        return Collections.unmodifiableCollection(buildSnapshot().values());
    }

    @Override
    public int getAcceptorCount() {
        int count = 0;
        for (Map<Direction, ACCEPTOR> faces : buildSnapshot().values()) {
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
    public ACCEPTOR getCachedAcceptor(long acceptorPos, Direction side) {
        return buildSnapshot().getOrDefault(acceptorPos, Collections.emptyMap()).get(side);
    }

    @Override
    public Set<Direction> getAcceptorDirections(long pos) {
        Map<Direction, ACCEPTOR> faces = buildSnapshot().get(pos);
        return faces == null ? Collections.emptySet() : EnumSet.copyOf(faces.keySet());
    }
}
