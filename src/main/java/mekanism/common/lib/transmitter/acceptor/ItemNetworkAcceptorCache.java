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
import mekanism.common.content.network.transmitter.LogisticalTransporterBase;
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.WorldUtils;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Item-only derived {@link NetworkAcceptorCache} specialization, reusing the reviewed design
 * of the fluid network acceptor cache: no external Fabric {@code Storage} is retained across ticks or copied on
 * adopt; every public reader builds a detached ephemeral position→face→{@link Storage} snapshot from the current
 * valid, loaded member transporters and re-reads providers on each read.
 *
 * <p>Discovery checks all six sides of each member transporter, running the official
 * {@code refreshAcceptorConnections} bitmap policy (packet only when that side's bit changes) before the
 * {@code canConnectToAcceptor}/acceptor decision, preserving redstone/mutual-transmission exclusions and
 * opposite-face keying. The acceptor is resolved through each member's per-pipe fresh
 * {@link ItemAcceptorCache} context.
 */
public class ItemNetworkAcceptorCache extends NetworkAcceptorCache<Storage<ItemVariant>> {

    private final Supplier<Collection<LogisticalTransporterBase>> membership;

    /**
     * @param membership Deferred supplier of the authoritative InventoryNetwork membership. Evaluated lazily on each
     *                   public read only, never during network construction.
     */
    public ItemNetworkAcceptorCache(Supplier<Collection<LogisticalTransporterBase>> membership) {
        this.membership = membership;
    }

    private Long2ObjectMap<Map<Direction, Storage<ItemVariant>>> buildSnapshot() {
        Long2ObjectMap<Map<Direction, Storage<ItemVariant>>> snapshot = new Long2ObjectOpenHashMap<>();
        for (LogisticalTransporterBase transporter : membership.get()) {
            //Client networks and unloaded/removed transmitter tiles contribute nothing; never load chunks for a read
            if (transporter == null || transporter.isRemote() || !transporter.isValid()) {
                continue;
            }
            for (Direction side : EnumUtils.DIRECTIONS) {
                //Official bitmap policy: refresh this side's acceptor-connection validity (and packet on change)
                transporter.refreshAcceptorConnections(side);
                if (!transporter.canConnectToAcceptor(side) || !transporter.canEmitTo(side)) {
                    continue;
                }
                Storage<ItemVariant> acceptor = transporter.getAcceptor(side);
                if (acceptor == null) {
                    continue;
                }
                long acceptorPos = WorldUtils.relativePos(transporter.getWorldPositionLong(), side);
                snapshot.computeIfAbsent(acceptorPos, pos -> new EnumMap<>(Direction.class)).put(side.getOpposite(), acceptor);
            }
        }
        return snapshot;
    }

    @Override
    public void updateTransmitterOnSide(Transmitter<Storage<ItemVariant>, ?, ?> transmitter, Direction side) {
        //Derived state: keep the real official connection bitmap refresh (and change packet), but do NOT retain the
        // returned Storage in the base map. Public readers re-derive it freshly on each read.
        transmitter.refreshAcceptorConnections(side);
    }

    @Override
    public void adoptAcceptors(NetworkAcceptorCache<Storage<ItemVariant>> other) {
        //No retained endpoint state exists to transfer: only pending dirty sides propagate so post-merge commit still
        // refreshes connection bitmaps; merged membership is re-read authoritatively by the next public read.
        adoptPendingDirty(other);
    }

    /**
     * @apiNote Listeners should not be added to these storages here as they may not correspond to an actual handler
     *         and may not get invalidated.
     */
    @Override
    public ObjectIterator<Long2ObjectMap.Entry<Map<Direction, Storage<ItemVariant>>>> getAcceptorFastIterator() {
        return Long2ObjectMaps.fastIterator(buildSnapshot());
    }

    /**
     * @apiNote Listeners should not be added to these storages here as they may not correspond to an actual handler
     *         and may not get invalidated.
     */
    @Override
    public Collection<Map<Direction, Storage<ItemVariant>>> getAcceptorValues() {
        return Collections.unmodifiableCollection(buildSnapshot().values());
    }

    @Override
    public int getAcceptorCount() {
        int count = 0;
        for (Map<Direction, Storage<ItemVariant>> faces : buildSnapshot().values()) {
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
    public Storage<ItemVariant> getCachedAcceptor(long acceptorPos, Direction side) {
        return buildSnapshot().getOrDefault(acceptorPos, Collections.emptyMap()).get(side);
    }

    @Override
    public Set<Direction> getAcceptorDirections(long pos) {
        Map<Direction, Storage<ItemVariant>> faces = buildSnapshot().get(pos);
        return faces == null ? Collections.emptySet() : EnumSet.copyOf(faces.keySet());
    }
}
