package mekanism.common.lib.chunkloading;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import mekanism.common.lib.MekanismSavedData;
import mekanism.common.tile.component.TileComponentChunkLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric port delta: replaces the NeoForge {@code TicketController}/{@code TicketHelper} owner. Vanilla's forced-chunk flag is
 * level data without an owner, so this saved data records which block position forced which chunks and revalidates the entries
 * after server start, mirroring the official loading-validation callback: entries whose owner block is gone or whose loader can
 * no longer operate are released, and operating loaders are resynchronized with the chunks they expect.
 *
 * <p>Vanilla has only one forced-chunk class (forced chunks tick), so the official non-ticking ticket distinction collapses;
 * this only affects loaders that registered non-ticking tickets, which still load but now also tick.</p>
 */
public class ChunkLoaderTickets extends MekanismSavedData {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism");
    private static final String OWNERS = "owners";
    private static final String OWNER_POS = "pos";
    private static final String CHUNKS = "chunks";

    private final Map<BlockPos, LongSet> owners = new HashMap<>();

    public static ChunkLoaderTickets get(ServerLevel level) {
        return MekanismSavedData.createSavedData(level.getDataStorage(),
              new SavedData.Factory<>(ChunkLoaderTickets::new, (tag, provider) -> {
                  ChunkLoaderTickets data = new ChunkLoaderTickets();
                  data.load(tag, provider);
                  return data;
              }, null), "chunk_loader_tickets");
    }

    /**
     * Sets or clears one owned chunk ticket. Returns whether the level accepted the request.
     */
    public static boolean setTicket(ServerLevel level, BlockPos owner, int x, int z, boolean force) {
        boolean result = level.setChunkForced(x, z, force);
        ChunkLoaderTickets data = get(level);
        long chunk = ChunkPos.asLong(x, z);
        if (force) {
            data.owners.computeIfAbsent(owner.immutable(), pos -> new LongOpenHashSet()).add(chunk);
        } else {
            LongSet chunks = data.owners.get(owner);
            if (chunks != null) {
                chunks.remove(chunk);
                if (chunks.isEmpty()) {
                    data.owners.remove(owner);
                }
            }
        }
        data.setDirty();
        return result;
    }

    /**
     * Revalidates every recorded owner after server start. Mirrors the official validation callback.
     */
    public static void validate(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            validate(level);
        }
    }

    private static void validate(ServerLevel level) {
        ChunkLoaderTickets data = get(level);
        Iterator<Map.Entry<BlockPos, LongSet>> it = data.owners.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, LongSet> entry = it.next();
            BlockPos ownerPos = entry.getKey();
            LongSet forcedChunks = entry.getValue();
            BlockEntity blockEntity = level.getBlockEntity(ownerPos);
            if (!(blockEntity instanceof IChunkLoader chunkLoaderOwner)) {
                LOGGER.warn("Block at {}, in {} is not a valid chunk loader. Removing {} chunk tickets.",
                      ownerPos, level.dimension().location(), forcedChunks.size());
                release(level, forcedChunks);
                it.remove();
                continue;
            }
            TileComponentChunkLoader<?> chunkLoader = chunkLoaderOwner.getChunkLoader();
            if (!chunkLoader.canOperate()) {
                LOGGER.info("Removing {} chunk tickets as they are no longer valid as this loader cannot operate. Pos: {} World: {}",
                      forcedChunks.size(), ownerPos, level.dimension().location());
                release(level, forcedChunks);
                it.remove();
                chunkLoader.clearRecordedTickets();
                continue;
            }
            //Trust the recorded tickets as the authoritative set and resynchronize the loader with them
            chunkLoader.adoptRecordedTickets(level, ownerPos, forcedChunks);
            LongSet expected = chunkLoader.getTileChunks();
            if (expected.isEmpty()) {
                LOGGER.warn("Removing {} chunk tickets as this loader does not expect to have any tickets even though it can operate. Pos: {} World: {}",
                      forcedChunks.size(), ownerPos, level.dimension().location());
                release(level, forcedChunks);
                it.remove();
                chunkLoader.clearRecordedTickets();
                continue;
            }
            //Remove tickets that are no longer valid and add the ones that now are
            int removed = 0;
            int added = 0;
            LongIterator chunkIt = forcedChunks.iterator();
            while (chunkIt.hasNext()) {
                long chunk = chunkIt.nextLong();
                if (!expected.contains(chunk)) {
                    level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
                    chunkIt.remove();
                    removed++;
                }
            }
            for (long chunk : expected) {
                if (forcedChunks.add(chunk)) {
                    level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
                    added++;
                }
            }
            if (removed != 0 || added != 0) {
                data.setDirty();
                LOGGER.info("validateTickets(): Removed {} no longer valid chunk tickets, and added {} newly valid chunk tickets. Pos: {} World: {}",
                      removed, added, ownerPos, level.dimension().location());
            }
        }
        if (data.owners.isEmpty()) {
            //Nothing to keep; avoid writing an empty entry back into level data
            data.setDirty(false);
        }
    }

    private static void release(ServerLevel level, LongSet chunks) {
        for (long chunk : chunks) {
            level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
        }
        chunks.clear();
    }

    public void removeOwner(BlockPos owner) {
        owners.remove(owner);
        setDirty();
    }

    @Override
    public void load(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider provider) {
        ListTag list = nbt.getList(OWNERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = BlockPos.of(entry.getLong(OWNER_POS));
            long[] chunks = entry.getLongArray(CHUNKS);
            if (chunks.length > 0) {
                LongSet set = new LongOpenHashSet(chunks.length);
                for (long chunk : chunks) {
                    set.add(chunk);
                }
                owners.put(pos, set);
            }
        }
    }

    @Override
    public CompoundTag save(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, LongSet> entry : owners.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                CompoundTag tag = new CompoundTag();
                tag.putLong(OWNER_POS, entry.getKey().asLong());
                tag.putLongArray(CHUNKS, entry.getValue().toLongArray());
                list.add(tag);
            }
        }
        nbt.put(OWNERS, list);
        return nbt;
    }
}
