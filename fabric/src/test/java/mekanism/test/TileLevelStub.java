package mekanism.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.Nullable;

/**
 * Real {@link Level} test double that models only the tile/chunk state needed by the base-tile lifecycle: an explicit chunk map, an explicit block-entity map, a sent
 * block-update counter, and the game time. It exists so reduced-update, coordinate caching, and save-coalescing behavior are exercised through real
 * {@code Level}/{@code LevelChunk} lookups instead of a mock that would silently accept production misbehavior. Anything it does not model fails closed.
 *
 * <p>Chunks are real {@link LevelChunk} instances, so {@code getChunk(..., false)} and {@code setUnsaved} are the vanilla implementations. An absent chunk stays absent
 * unless the test installs it, which is what makes "no unloaded-chunk creation" observable.</p>
 */
public final class TileLevelStub extends RecipeLevelStub {

    private final Map<Long, LevelChunk> chunks = new HashMap<>();
    private final Map<BlockPos, BlockEntity> tiles = new HashMap<>();
    private boolean remote;
    private int sentBlockUpdates;
    private long gameTime;
    private int fullChunkLookups;

    private TileLevelStub(ResourceKey<Level> dimension, RegistryAccess access, Holder<DimensionType> dimensionType) {
        super(new StubLevelData(), dimension, access, dimensionType, () -> null, null);
    }

    /** A level whose registry access carries the damage-type registry the real {@link Level} constructor needs plus a biome registry for real chunks. */
    public static TileLevelStub create() {
        return create(Level.OVERWORLD);
    }

    public static TileLevelStub create(ResourceKey<Level> dimension) {
        return create(dimension, null);
    }

    public static TileLevelStub createWithRegistry(Registry<?> registry) {
        return create(Level.OVERWORLD, registry);
    }

    private static TileLevelStub create(ResourceKey<Level> dimension, @Nullable Registry<?> extraRegistry) {
        MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, com.mojang.serialization.Lifecycle.stable());
        biomes.register(Biomes.PLAINS, plains(), RegistrationInfo.BUILT_IN);
        biomes.register(Biomes.THE_VOID, plains(), RegistrationInfo.BUILT_IN);
        RegistryAccess damageTypes = damageTypeAccess();
        List<Registry<?>> registries = new ArrayList<>();
        registries.add(damageTypes.registryOrThrow(Registries.DAMAGE_TYPE));
        registries.add(biomes);
        if (extraRegistry != null) registries.add(extraRegistry);
        RegistryAccess access = new RegistryAccess.ImmutableRegistryAccess(registries);
        return new TileLevelStub(dimension, access, Holder.direct(new DimensionType(
              java.util.OptionalLong.empty(), true, false, false, true, 1.0D, true, false,
              DimensionType.MIN_Y, DimensionType.Y_SIZE, DimensionType.Y_SIZE,
              net.minecraft.tags.BlockTags.INFINIBURN_OVERWORLD, net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD_EFFECTS, 0.0F,
              new DimensionType.MonsterSettings(false, false, net.minecraft.util.valueproviders.ConstantInt.of(0), 0))));
    }

    private static Biome plains() {
        return new Biome.BiomeBuilder()
              .hasPrecipitation(true)
              .temperature(0.8F)
              .downfall(0.4F)
              .specialEffects(new BiomeSpecialEffects.Builder()
                    .waterColor(4159204)
                    .waterFogColor(329011)
                    .fogColor(12638463)
                    .skyColor(7907327)
                    .build())
              .mobSpawnSettings(MobSpawnSettings.EMPTY)
              .generationSettings(BiomeGenerationSettings.EMPTY)
              .build();
    }

    /** Installs a real chunk created through the genuine {@link LevelChunk} constructors. */
    public void installChunk(int chunkX, int chunkZ) {
        chunks.put(ChunkPos.asLong(chunkX, chunkZ), new LevelChunk(this, new ChunkPos(chunkX, chunkZ)));
    }

    public boolean hasChunkInstalled(int chunkX, int chunkZ) {
        return chunks.containsKey(ChunkPos.asLong(chunkX, chunkZ));
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return hasChunkInstalled(chunkX, chunkZ);
    }

    public boolean isChunkUnsaved(int chunkX, int chunkZ) {
        LevelChunk chunk = chunks.get(ChunkPos.asLong(chunkX, chunkZ));
        if (chunk == null) {
            throw new IllegalStateException("No chunk installed at " + chunkX + "," + chunkZ);
        }
        return chunk.isUnsaved();
    }

    public void clearChunkUnsaved(int chunkX, int chunkZ) {
        LevelChunk chunk = chunks.get(ChunkPos.asLong(chunkX, chunkZ));
        if (chunk != null) {
            chunk.setUnsaved(false);
        }
    }

    public int sentBlockUpdates() {
        return sentBlockUpdates;
    }

    public void setRemote(boolean remote) {
        this.remote = remote;
    }

    /** Sets the tick clock the base tile reads for its per-tick save coalescing. */
    public void setGameTime(long gameTime) {
        this.gameTime = gameTime;
    }

    @Override
    public long getGameTime() {
        return gameTime;
    }

    /** Registers a tile so {@code getBlockEntity} resolves it, and gives it this level. */
    public <T extends BlockEntity> T installTile(T tile) {
        tiles.put(tile.getBlockPos(), tile);
        tile.setLevel(this);
        return tile;
    }

    public void removeTile(BlockEntity tile) {
        tiles.remove(tile.getBlockPos());
    }

    @Override
    @Nullable
    public LevelChunk getChunk(int chunkX, int chunkZ) {
        return chunks.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    /** Counts the non-loading FULL chunk lookups the dirty-marking path performs, which is how coalescing becomes observable. */
    public int fullChunkLookups() {
        return fullChunkLookups;
    }

    @Override
    @Nullable
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean requireChunk) {
        if (status == ChunkStatus.FULL && !requireChunk) {
            fullChunkLookups++;
        }
        LevelChunk chunk = chunks.get(ChunkPos.asLong(x, z));
        if (chunk == null && requireChunk) {
            throw new IllegalStateException("TileLevelStub never creates a chunk");
        }
        return chunk;
    }

    @Override
    public int getHeight() {
        return DimensionType.Y_SIZE;
    }

    @Override
    public int getMinBuildHeight() {
        return DimensionType.MIN_Y;
    }

    @Override
    public ChunkSource getChunkSource() {
        return new StubChunkSource(this);
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return tiles.get(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockEntity tile = tiles.get(pos);
        return tile == null ? Blocks.AIR.defaultBlockState() : tile.getBlockState();
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return chunks.containsKey(ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())));
    }

    @Override
    public boolean isClientSide() {
        return remote;
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        sentBlockUpdates++;
    }

    /** Real chunk source over the explicit map; never creates a chunk. */
    private static final class StubChunkSource extends ChunkSource {

        private final TileLevelStub level;

        private StubChunkSource(TileLevelStub level) {
            this.level = level;
        }

        @Override
        @Nullable
        public ChunkAccess getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
            return level.getChunk(x, z, chunkStatus, requireChunk);
        }

        @Override
        @Nullable
        public net.minecraft.world.level.chunk.LightChunk getChunkForLighting(int chunkX, int chunkZ) {
            return null;
        }

        @Override
        public net.minecraft.world.level.BlockGetter getLevel() {
            return level;
        }

        @Override
        public void tick(BooleanSupplier hasTimeLeft, boolean tickChunks) {
            throw unsupported();
        }

        @Override
        public String gatherStats() {
            return "TileLevelStub";
        }

        @Override
        public int getLoadedChunksCount() {
            return level.chunks.size();
        }

        @Override
        public LevelLightEngine getLightEngine() {
            throw unsupported();
        }
    }

    /** Inert level data; only game time and difficulty are ever read through the real {@link Level} paths. */
    private static final class StubLevelData implements WritableLevelData {

        @Override
        public void setSpawn(BlockPos pos, float angle) {
            throw unsupported();
        }

        @Override
        public BlockPos getSpawnPos() {
            return BlockPos.ZERO;
        }

        @Override
        public float getSpawnAngle() {
            return 0;
        }

        @Override
        public long getGameTime() {
            return 0;
        }

        @Override
        public long getDayTime() {
            return 0;
        }

        @Override
        public boolean isThundering() {
            return false;
        }

        @Override
        public boolean isRaining() {
            return false;
        }

        @Override
        public void setRaining(boolean raining) {
            throw unsupported();
        }

        @Override
        public boolean isHardcore() {
            return false;
        }

        @Override
        public GameRules getGameRules() {
            return new GameRules();
        }

        @Override
        public Difficulty getDifficulty() {
            return Difficulty.NORMAL;
        }

        @Override
        public boolean isDifficultyLocked() {
            return false;
        }

        @Override
        public void fillCrashReportCategory(net.minecraft.CrashReportCategory category, net.minecraft.world.level.LevelHeightAccessor accessor) {
            throw unsupported();
        }
    }
}
