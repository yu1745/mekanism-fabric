package mekanism.common;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.function.Predicate;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.WorldUtils;
import mekanism.common.world.GenHandler;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric port delta: the official NeoForge event listeners become Fabric lifecycle registrations.
 *
 * <ul>
 * <li>{@code EntityJoinLevelEvent} → {@link ServerEntityEvents#ENTITY_LOAD}: Fabric cannot veto the add, so the same entities
 * are discarded instead of discarded-and-cancelled, which is the observable outcome the official handler produced.</li>
 * <li>{@code ChunkDataEvent.Save/Load} → the original root NBT field through ChunkGenVersionMixin. A transient attachment
 * carries the loaded version with the actual chunk until it is saved; chunks generated in this session save the current version.</li>
 * <li>{@code LevelTickEvent.Post} → Fabric's world tick for retrogen only. Frequency and radiation keep their existing owners.</li>
 * <li>{@code BlockEvent.BreakEvent} security veto → {@code ServerPlayerGameModeMixin}, which applies the same block security
 * access check before a real player break.</li>
 * </ul>
 */
public class CommonWorldTickHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism");
    /** Fabric port delta: the excluded entrypoint's {@code worldTickHandler} field is owned here. */
    public static final CommonWorldTickHandler INSTANCE = new CommonWorldTickHandler();
    private static final long maximumDeltaTimeNanoSecs = 16_000_000; // 16 milliseconds

    /**
     * World-gen version stored on the chunk itself (the official code wrote it into the chunk's save data).
     */
    public static final AttachmentType<Integer> WORLD_GEN_VERSION = AttachmentRegistry.create(
          ResourceLocation.fromNamespaceAndPath("mekanism", "world_gen_version"));

    private Map<ResourceLocation, Queue<ChunkPos>> chunkRegenMap;
    public static boolean monitoringCardboardBox;
    @Nullable
    public static Predicate<ItemStack> fallbackItemCollector;

    /** Wires the world/entity/chunk/tick behavior to Fabric lifecycle events. Called once from the mod bootstrap. */
    public static void register() {
        CommonWorldTickHandler handler = INSTANCE;
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> handler.onEntitySpawn(entity, level));
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> handler.onChunkDataLoad(level, chunk));
        ServerTickEvents.END_WORLD_TICK.register(handler::onLevelTick);
    }

    public void addRegenChunk(ResourceKey<Level> dimension, ChunkPos chunkCoord) {
        if (chunkRegenMap == null) {
            chunkRegenMap = new Object2ObjectArrayMap<>();
        }
        ResourceLocation dimensionName = dimension.location();
        if (!chunkRegenMap.containsKey(dimensionName)) {
            LinkedList<ChunkPos> list = new LinkedList<>();
            list.add(chunkCoord);
            chunkRegenMap.put(dimensionName, list);
        } else {
            Queue<ChunkPos> regenPositions = chunkRegenMap.get(dimensionName);
            if (!regenPositions.contains(chunkCoord)) {
                regenPositions.add(chunkCoord);
            }
        }
    }

    public void resetChunkData() {
        chunkRegenMap = null;
    }

    public void onEntitySpawn(Entity entity, ServerLevel level) {
        //If we are in the middle of breaking a block using a cardboard box, cancel any items
        // that are dropped, we do this as early as possible to ensure it happens the same tick.
        // We also cancel any experience orbs from spawning as things like the furnace will store
        // how much xp they have but also try to drop it on replace
        if (monitoringCardboardBox) {
            if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                entity.discard();
            }
        } else if (!level.isClientSide && fallbackItemCollector != null && entity instanceof ItemEntity item && fallbackItemCollector.test(item.getItem())) {
            //If we have a fallback item collector active and the entity that is being added is an item,
            // try to let our fallback collector handle the item and keep track of it instead of actually adding it to the world
            entity.discard();
        }
    }

    private void onChunkDataLoad(ServerLevel level, LevelChunk chunk) {
        Integer version = chunk.getAttached(WORLD_GEN_VERSION);
        //When a chunk is loaded, if it has an older version than the latest one
        if (version != null && version < MekanismConfig.world.userGenVersion.get()) {
            if (MekanismConfig.world.enableRegeneration.get()) {
                //If retrogen is enabled, then we also need to mark the chunk as needing retrogen
                addRegenChunk(level.dimension(), chunk.getPos());
            }
        }
    }

    private void onLevelTick(ServerLevel world) {
        //Frequency and radiation keep their existing Fabric lifecycle owners; only retrogen is owned here.
        //Note: We flush the tag and recipe cache, and also perform retrogen, regardless of if the ticks are frozen or not
        //The original portable QIO recipe refresh is owned by FabricRecipeCacheLifecycle after all worlds/frequencies tick.
        //Its signal is part of RecipeCacheContext.Binding, not a process-global flag cleared by the first world.

        if (chunkRegenMap == null || !MekanismConfig.world.enableRegeneration.get()) {
            return;
        }
        ResourceLocation dimensionName = world.dimension().location();
        //Credit to E. Beef
        if (chunkRegenMap.containsKey(dimensionName)) {
            Queue<ChunkPos> chunksToGen = chunkRegenMap.get(dimensionName);
            long startTime = System.nanoTime();
            while (System.nanoTime() - startTime < maximumDeltaTimeNanoSecs && !chunksToGen.isEmpty()) {
                ChunkPos nextChunk = chunksToGen.poll();
                if (nextChunk == null) {
                    break;
                }
                //Ensure the chunk actually exists and is still loaded before trying to retrogen it
                if (WorldUtils.isChunkLoaded(world, nextChunk)) {
                    if (GenHandler.generate(world, nextChunk)) {
                        LOGGER.info("Regenerating ores and salt at chunk {}", nextChunk);
                    }
                    //Regardless of whether we were able to generate anything in the chunk, now that we have
                    // handled it, update the version that the original root NBT field will save.
                    LevelChunk chunk = world.getChunk(nextChunk.x, nextChunk.z);
                    chunk.setAttached(WORLD_GEN_VERSION, MekanismConfig.world.userGenVersion.get());
                }
            }
            if (chunksToGen.isEmpty()) {
                chunkRegenMap.remove(dimensionName);
            }
        }
    }
}
