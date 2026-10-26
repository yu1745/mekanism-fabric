package mekanism.common.tile.component;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import mekanism.api.MekanismAPI;
import mekanism.api.SerializationConstants;
import mekanism.api.Upgrade;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.chunkloading.ChunkLoaderTickets;
import mekanism.common.lib.chunkloading.IChunkLoader;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class TileComponentChunkLoader<T extends TileEntityMekanism & IChunkLoader> implements ITileComponent {

    private static final Logger LOGGER = LogUtils.getLogger();
    //Fabric port delta: the NeoForge TicketController is replaced by the owned vanilla-forced-chunk seam. The id the
    // controller used for ticket persistence is not needed because ChunkLoaderTickets persists the owners itself.

    /**
     * TileEntity implementing this component.
     */
    private final T tile;
    private final LongSet chunkSet = new LongOpenHashSet();
    private final boolean forceTicks;
    @Nullable
    private ServerLevel prevWorld;
    @Nullable
    private BlockPos prevPos;
    private boolean hasRegistered;

    public TileComponentChunkLoader(T tile) {
        this(tile, false);
    }

    public TileComponentChunkLoader(T tile, boolean forceTicks) {
        this.tile = tile;
        this.tile.addComponent(this);
        this.forceTicks = forceTicks;
    }

    public boolean canOperate() {
        return MekanismConfig.general.allowChunkloading.get() && tile.supportsUpgrades() && tile.getComponent().isUpgradeInstalled(Upgrade.ANCHOR);
    }

    private void releaseChunkTickets(@NotNull ServerLevel world, @NotNull BlockPos pos) {
        int tickets = chunkSet.size();
        LOGGER.debug("Attempting to remove {} chunk tickets. Pos: {} World: {}", tickets, pos, world.dimension().location());
        if (tickets > 0) {
            for (long chunkPos : chunkSet) {
                boolean success = ChunkLoaderTickets.setTicket(world, pos, ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos), false);
                if (!success) {
                    LOGGER.warn("Failed to release chunk ticket for {}", chunkPos);
                }
            }
            chunkSet.clear();
            markDirty();
        }
        hasRegistered = false;
        prevWorld = null;
    }

    private void registerChunkTickets(@NotNull ServerLevel world) {
        prevPos = tile.getBlockPos();
        prevWorld = world;
        Set<ChunkPos> chunks = tile.getChunkSet();
        int tickets = chunks.size();
        LOGGER.debug("Attempting to add {} chunk tickets. Pos: {} World: {}", tickets, prevPos, world.dimension().location());
        if (tickets > 0) {
            for (ChunkPos chunkPos : chunks) {
                boolean success = ChunkLoaderTickets.setTicket(world, prevPos, chunkPos.x, chunkPos.z, true);
                chunkSet.add(chunkPos.toLong());
                if (!success) {
                    LOGGER.error("Failed to force chunk during registration {}", chunkPos);
                }
            }
            markDirty();
        }
        hasRegistered = true;
    }

    /**
     * Release and re-register tickets, call when chunk set changes
     */
    public void refreshChunkTickets() {
        if (!tile.isRemote()) {
            refreshChunkTickets((ServerLevel) Objects.requireNonNull(tile.getLevel()), tile.getBlockPos(), true);
        }
    }

    /**
     * @param ticketsChanged {@code true} if the chunk set of our tile changed, and we need to force adjusting our registered tickets.
     *
     * @apiNote Only call server side
     */
    private void refreshChunkTickets(@NotNull ServerLevel world, @NotNull BlockPos pos, boolean ticketsChanged) {
        boolean canOperate = canOperate();
        if (MekanismAPI.debug) {
            LOGGER.debug("refreshChunkTickets called for {}. Can operate = {}", pos, canOperate);
        }
        if (hasRegistered && prevWorld != null && prevPos != null) {
            //Note: If we have already registered the previous world and previous position
            // should never be null, but we validate this just in case
            if (prevWorld != world || !pos.equals(prevPos)) {
                //If the location changed clear all old tickets
                releaseChunkTickets(prevWorld, prevPos);
                if (canOperate) {
                    //And if we can operate, add any new ones back
                    registerChunkTickets(world);
                }
            } else if (!canOperate) {
                //If we can't operate any more clear all old tickets
                releaseChunkTickets(world, pos);
            } else if (ticketsChanged) {
                //We can operate, the location didn't change. If the tickets changed, remove ones that are no longer valid
                // and add in any that are now valid but were not before. This lets us not have to remove and then add back
                // any tickets that are still valid as it is unnecessary.
                if (chunkSet.isEmpty()) {
                    //If we registered but don't actually have any loaded, which is somewhat unlikely, but worth checking;
                    // just register our tickets normally
                    registerChunkTickets(world);
                } else {
                    //Otherwise, we need to do some more checks
                    LongSet newChunks = getTileChunks();
                    if (newChunks.isEmpty()) {
                        //Probably never the case, but if we have no chunks that should be loaded anymore;
                        // just release them all normally
                        releaseChunkTickets(world, pos);
                    } else {
                        //Otherwise, we need to do calculate the differences to properly adjust which chunks are loaded and which ones are not
                        int removed = 0;
                        int added = 0;
                        //Remove any chunk tickets that are not valid anymore
                        LongIterator chunkIt = chunkSet.iterator();
                        while (chunkIt.hasNext()) {
                            long chunkPos = chunkIt.nextLong();
                            if (!newChunks.contains(chunkPos)) {
                                //If the chunk is no longer in our chunks we want loaded
                                // then we need to unforce the chunk and remove it
                                boolean success = ChunkLoaderTickets.setTicket(world, pos, ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos), false);
                                if (!success) {
                                    LOGGER.warn("Failed to remove forced chunk {}", chunkPos);
                                }
                                chunkIt.remove();
                                removed++;
                            }
                        }
                        //And add any that are valid now that weren't before
                        for (long chunkPos : newChunks) {
                            if (chunkSet.add(chunkPos)) {
                                //If we didn't already have it in our chunk set and added actually added it as it is new
                                // then we also need to force the chunk
                                boolean success = ChunkLoaderTickets.setTicket(world, pos, ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos), true);
                                if (!success) {
                                    LOGGER.error("Failed to force chunk during refresh {}", chunkPos);
                                }
                                added++;
                            }
                        }
                        if (removed != 0 || added != 0) {
                            markDirty();
                        }
                        LOGGER.debug("refreshChunkTickets(): Removed {} no longer valid chunk tickets, and added {} newly valid chunk tickets. Pos: {} World: {}", removed, added, pos,
                              world.dimension().location());
                        if (MekanismAPI.debug) {
                            LOGGER.debug("Current set: {}", chunkSet);
                            LOGGER.debug("Tile chunk: {}", ChunkPos.asLong(tile.getBlockPos()));
                            LOGGER.debug("Tile requested: {}", newChunks);
                        }
                    }
                }
            }
        } else if (canOperate) {
            //We haven't registered yet, but we can operate - add all of our tickets
            registerChunkTickets(world);
        }
    }

    public void tickServer() {
        Level world = tile.getLevel();
        if (world != null) {
            //Update tickets if the position changed, or we are no longer able to operate
            refreshChunkTickets((ServerLevel) world, tile.getBlockPos(), false);
        }
    }

    @Override
    public String getComponentKey() {
        //Unused
        return "componentChunkLoader";
    }

    @Override
    public void deserialize(CompoundTag componentTag, HolderLookup.Provider provider) {
    }

    @Override
    public CompoundTag serialize(HolderLookup.Provider provider) {
        return new CompoundTag();
    }

    @Override
    public void read(CompoundTag nbtTags, HolderLookup.Provider provider) {
        if (!chunkSet.isEmpty()) {
            //If we currently have any chunks loaded, remove their tickets and clear them
            if (tile.hasLevel() && !tile.isRemote() && hasRegistered && prevWorld != null && prevPos != null) {
                //If we had any chunks registered remove them. When hasRegistered is true
                // prevWorld and prevPos should both be nonnull, but validate them just in case
                releaseChunkTickets(prevWorld, prevPos);
            } else {
                //Otherwise, just clear the set if it somehow has elements but isn't marked as registered
                chunkSet.clear();
            }
        }
        for (long chunk : nbtTags.getLongArray(SerializationConstants.CHUNK_SET)) {
            chunkSet.add(chunk);
        }
    }

    @Override
    public void write(CompoundTag nbtTags, HolderLookup.Provider provider) {
        if (!chunkSet.isEmpty()) {
            nbtTags.putLongArray(SerializationConstants.CHUNK_SET, chunkSet.toLongArray());
        }
    }

    @Override
    public void removed() {
        if (!tile.isRemote() && hasRegistered && prevWorld != null && prevPos != null) {
            //If we have any chunks registered remove them. When hasRegistered is true
            // prevWorld and prevPos should both be nonnull, but validate them just in case
            releaseChunkTickets(prevWorld, prevPos);
        }
    }

    private void markDirty() {
        //Marks the chunk as dirty so it can properly save
        tile.markForSave();
    }

    public LongSet getTileChunks() {
        Set<ChunkPos> chunks = tile.getChunkSet();
        if (chunks.isEmpty()) {
            return LongSets.EMPTY_SET;
        }
        LongSet chunksAsLongs = new LongOpenHashSet(chunks.size());
        for (ChunkPos chunkPos : chunks) {
            chunksAsLongs.add(chunkPos.toLong());
        }
        return chunksAsLongs;
    }

    /** Revalidates every persisted ticket after server start (official loading-validation callback replacement). */
    public static void validateTickets(@NotNull MinecraftServer server) {
        ChunkLoaderTickets.validate(server);
    }

    /** Records the ticket set the persisted data holds for this owner; used by the post-start validation. */
    public void adoptRecordedTickets(@NotNull ServerLevel world, @NotNull BlockPos pos, @NotNull LongSet recorded) {
        chunkSet.clear();
        chunkSet.addAll(recorded);
        hasRegistered = true;
        prevWorld = world;
        prevPos = pos;
        markDirty();
    }

    /** Clears the loader's own record after its tickets were released by validation. */
    public void clearRecordedTickets() {
        chunkSet.clear();
        hasRegistered = false;
        prevWorld = null;
        prevPos = null;
        markDirty();
    }
}
