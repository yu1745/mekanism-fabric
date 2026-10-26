package mekanism.common.lib.chunkloading;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.Map;
import mekanism.common.lib.MekanismSavedData;
import mekanism.common.tile.component.TileComponentChunkLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * Mekanism's persistent block-owner tickets. Region distance two gives the same level 31 ticket as the
 * official chunk controller, with a distinct key for each loader and no mutation of vanilla /forceload data.
 * All tickets allow block-entity/entity ticking; forceTicks additionally enables the official remote random-tick/spawn gate.
 */
public class ChunkLoaderTickets extends MekanismSavedData {

    private static final TicketType<BlockPos> TICKET = TicketType.create("mekanism:chunk_loader", Vec3i::compareTo);
    private static final int DISTANCE = 2;
    private static final String OWNERS = "owners";
    private static final String OWNER_POS = "pos";
    private static final String CHUNKS = "chunks";
    private static final String FORCE_TICKS = "force_ticks";

    private record OwnerTickets(LongSet chunks, boolean forceTicks) {
    }

    private final Map<BlockPos, OwnerTickets> owners = new HashMap<>();

    public static ChunkLoaderTickets get(ServerLevel level) {
        return MekanismSavedData.createSavedData(level.getDataStorage(),
              new SavedData.Factory<>(ChunkLoaderTickets::new, (tag, provider) -> {
                  ChunkLoaderTickets data = new ChunkLoaderTickets();
                  data.load(tag, provider);
                  return data;
              }, null), "chunk_loader_tickets");
    }

    /** Region-ticket operations are idempotent and cannot release another loader's or vanilla's ticket. */
    public static boolean setTicket(ServerLevel level, BlockPos owner, int x, int z, boolean force, boolean forceTicks) {
        ChunkLoaderTickets data = get(level);
        long chunk = ChunkPos.asLong(x, z);
        if (force) {
            OwnerTickets tickets = data.owners.get(owner);
            if (tickets == null) {
                tickets = new OwnerTickets(new LongOpenHashSet(), forceTicks);
                data.owners.put(owner.immutable(), tickets);
            } else if (tickets.forceTicks != forceTicks) {
                tickets = new OwnerTickets(tickets.chunks, forceTicks);
                data.owners.put(owner.immutable(), tickets);
            }
            tickets.chunks.add(chunk);
            level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(x, z), DISTANCE, owner.immutable());
        } else {
            level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(x, z), DISTANCE, owner);
            OwnerTickets tickets = data.owners.get(owner);
            if (tickets != null) {
                tickets.chunks.remove(chunk);
                if (tickets.chunks.isEmpty()) {
                    data.owners.remove(owner);
                }
            }
        }
        data.setDirty();
        return true;
    }

    public static boolean shouldForceTicks(ServerLevel level, ChunkPos chunk) {
        long packed = chunk.toLong();
        for (OwnerTickets tickets : get(level).owners.values()) {
            if (tickets.forceTicks && tickets.chunks.contains(packed)) {
                return true;
            }
        }
        return false;
    }

    /** Revalidates saved owners and reinstalls every surviving in-memory region ticket after startup. */
    public static void validate(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            ChunkLoaderTickets data = get(level);
            //Loading an owner's block entity can register tickets: never iterate the live owner map here.
            Map<BlockPos, OwnerTickets> recorded = new HashMap<>();
            data.owners.forEach((owner, tickets) -> recorded.put(owner,
                  new OwnerTickets(new LongOpenHashSet(tickets.chunks), tickets.forceTicks)));
            recorded.forEach((owner, tickets) -> data.validateOwner(level, owner, tickets));
        }
    }

    private void validateOwner(ServerLevel level, BlockPos owner, OwnerTickets recorded) {
        BlockEntity blockEntity = level.getBlockEntity(owner);
        TileComponentChunkLoader<?> loader = blockEntity instanceof IChunkLoader chunkLoader ? chunkLoader.getChunkLoader() : null;
        LongSet expected = loader != null && loader.canOperate() ? loader.getTileChunks() : new LongOpenHashSet();
        //Include any reentrant registration performed while loading the owner's chunk.
        OwnerTickets live = owners.get(owner);
        LongSet previous = new LongOpenHashSet(recorded.chunks);
        if (live != null) {
            previous.addAll(live.chunks);
        }
        for (long chunk : previous) {
            if (!expected.contains(chunk)) {
                level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(chunk), DISTANCE, owner);
            }
        }
        if (expected.isEmpty()) {
            owners.remove(owner);
            if (loader != null) {
                loader.clearRecordedTickets();
            }
        } else {
            //Native tickets are not persisted by Minecraft. Replay unchanged tickets as well as newly requested ones.
            owners.put(owner.immutable(), new OwnerTickets(new LongOpenHashSet(expected), loader.forcesTicks()));
            for (long chunk : expected) {
                level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(chunk), DISTANCE, owner);
            }
            loader.adoptRecordedTickets(level, owner, expected);
        }
        //An empty saved owner list must overwrite stale records on disk too.
        setDirty();
    }

    @Override
    public void load(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider provider) {
        owners.clear();
        ListTag list = nbt.getList(OWNERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            LongSet chunks = new LongOpenHashSet(entry.getLongArray(CHUNKS));
            if (!chunks.isEmpty()) {
                owners.put(BlockPos.of(entry.getLong(OWNER_POS)), new OwnerTickets(chunks, entry.getBoolean(FORCE_TICKS)));
            }
        }
    }

    @Override
    public CompoundTag save(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, OwnerTickets> entry : owners.entrySet()) {
            if (!entry.getValue().chunks.isEmpty()) {
                CompoundTag tag = new CompoundTag();
                tag.putLong(OWNER_POS, entry.getKey().asLong());
                tag.putLongArray(CHUNKS, entry.getValue().chunks.toLongArray());
                tag.putBoolean(FORCE_TICKS, entry.getValue().forceTicks);
                list.add(tag);
            }
        }
        nbt.put(OWNERS, list);
        return nbt;
    }
}
