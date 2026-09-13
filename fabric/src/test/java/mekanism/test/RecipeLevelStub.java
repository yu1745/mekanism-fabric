package mekanism.test;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal real {@link Level} test double that satisfies the whole abstract surface while modelling only recipe lookup.
 *
 * <p>It exists so the exact-manager mismatch contract can be exercised through the real {@code Level#getRecipeManager()} path instead of a misleading test name. Only
 * {@link #getRecipeManager()}, {@link #registryAccess()} and {@link #dimensionType()} return values; every other member fails closed, so the double cannot silently
 * stand in for production behavior it does not model.</p>
 */
public final class RecipeLevelStub extends Level {

    private final RecipeManager manager;
    private final Holder<DimensionType> dimensionType;

    private RecipeLevelStub(WritableLevelData data, ResourceKey<Level> dimension, RegistryAccess access, Holder<DimensionType> dimensionType,
          Supplier<ProfilerFiller> profiler, RecipeManager manager) {
        super(data, dimension, access, dimensionType, profiler, false, false, 0L, 0);
        this.manager = manager;
        this.dimensionType = dimensionType;
    }

    /**
     * Creates a level whose {@link #getRecipeManager()} returns exactly the given manager.
     *
     * <p>The real {@link Level} constructor resolves the datapack {@code damage_type} registry and dereferences the dimension type, so this builds a minimal
     * damage-type registry containing every vanilla key plus a direct holder over an overworld-shaped dimension type. Neither registry is mutated globally, and
     * neither participates in recipe resolution.</p>
     */
    public static RecipeLevelStub of(RecipeManager manager) {
        Holder<DimensionType> dimensionType = Holder.direct(new DimensionType(
              java.util.OptionalLong.empty(), true, false, false, true, 1.0D, true, false,
              DimensionType.MIN_Y, DimensionType.Y_SIZE, DimensionType.Y_SIZE,
              net.minecraft.tags.BlockTags.INFINIBURN_OVERWORLD, net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD_EFFECTS, 0.0F,
              new DimensionType.MonsterSettings(false, false, net.minecraft.util.valueproviders.ConstantInt.of(0), 0)));
        return new RecipeLevelStub(new StubLevelData(), Level.OVERWORLD, damageTypeAccess(), dimensionType, () -> null, manager);
    }

    /**
     * Builds a registry access holding only a complete vanilla damage-type registry, which is all the {@link Level} constructor needs from a datapack registry.
     */
    private static RegistryAccess damageTypeAccess() {
        net.minecraft.core.MappedRegistry<net.minecraft.world.damagesource.DamageType> registry =
              new net.minecraft.core.MappedRegistry<>(Registries.DAMAGE_TYPE, com.mojang.serialization.Lifecycle.stable());
        net.minecraft.world.damagesource.DamageType dummy = new net.minecraft.world.damagesource.DamageType("generic", 0.0F);
        for (java.lang.reflect.Field field : net.minecraft.world.damagesource.DamageTypes.class.getDeclaredFields()) {
            if (field.getType() != ResourceKey.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                ResourceKey<net.minecraft.world.damagesource.DamageType> key =
                      (ResourceKey<net.minecraft.world.damagesource.DamageType>) field.get(null);
                registry.register(key, dummy, net.minecraft.core.RegistrationInfo.BUILT_IN);
            } catch (IllegalAccessException | RuntimeException failure) {
                throw new IllegalStateException("Unable to build the test damage-type registry", failure);
            }
        }
        return new RegistryAccess.ImmutableRegistryAccess(List.of(registry));
    }

    @Override
    public RecipeManager getRecipeManager() {
        return manager;
    }

    @Override
    public RegistryAccess registryAccess() {
        return super.registryAccess();
    }

    @Override
    public Holder<DimensionType> dimensionTypeRegistration() {
        return dimensionType;
    }

    @Override
    public DimensionType dimensionType() {
        return dimensionType.value();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("RecipeLevelStub models only recipe lookup");
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        throw unsupported();
    }

    @Override
    public void playSeededSound(@Nullable Player player, double x, double y, double z, Holder<net.minecraft.sounds.SoundEvent> sound,
          net.minecraft.sounds.SoundSource source, float volume, float pitch, long seed) {
        throw unsupported();
    }

    @Override
    public void playSeededSound(@Nullable Player player, Entity entity, Holder<net.minecraft.sounds.SoundEvent> sound,
          net.minecraft.sounds.SoundSource source, float volume, float pitch, long seed) {
        throw unsupported();
    }

    @Override
    public void playSound(@Nullable Player player, BlockPos pos, net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source,
          float volume, float pitch) {
        throw unsupported();
    }

    @Override
    public void addParticle(net.minecraft.core.particles.ParticleOptions particle, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        throw unsupported();
    }

    @Override
    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) {
        throw unsupported();
    }

    @Override
    public void gameEvent(Holder<net.minecraft.world.level.gameevent.GameEvent> event, net.minecraft.world.phys.Vec3 position,
          net.minecraft.world.level.gameevent.GameEvent.Context context) {
        throw unsupported();
    }

    @Override
    public String gatherChunkSourceStats() {
        throw unsupported();
    }

    @Override
    @Nullable
    public Entity getEntity(int id) {
        throw unsupported();
    }

    @Override
    public net.minecraft.world.TickRateManager tickRateManager() {
        throw unsupported();
    }

    @Override
    @Nullable
    public MapItemSavedData getMapData(MapId id) {
        throw unsupported();
    }

    @Override
    public void setMapData(MapId id, MapItemSavedData data) {
        throw unsupported();
    }

    @Override
    public MapId getFreeMapId() {
        throw unsupported();
    }

    @Override
    public void destroyBlockProgress(int id, BlockPos pos, int progress) {
        throw unsupported();
    }

    @Override
    public net.minecraft.world.scores.Scoreboard getScoreboard() {
        throw unsupported();
    }

    @Override
    protected net.minecraft.world.level.entity.LevelEntityGetter<Entity> getEntities() {
        throw unsupported();
    }

    @Override
    public net.minecraft.world.item.alchemy.PotionBrewing potionBrewing() {
        throw unsupported();
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        throw unsupported();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        throw unsupported();
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
        throw unsupported();
    }

    @Override
    public int getHeight() {
        throw unsupported();
    }

    @Override
    public int getHeight(Heightmap.Types heightmap, int x, int z) {
        throw unsupported();
    }

    @Override
    public int getMinBuildHeight() {
        throw unsupported();
    }

    @Override
    public int getSeaLevel() {
        throw unsupported();
    }

    @Override
    public int getSkyDarken() {
        throw unsupported();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        throw unsupported();
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        throw unsupported();
    }

    @Override
    public List<? extends Player> players() {
        throw unsupported();
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity entity, AABB area, Predicate<? super Entity> predicate) {
        throw unsupported();
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> typeTest, AABB area, Predicate<? super T> predicate) {
        throw unsupported();
    }

    @Override
    public long nextSubTickCount() {
        throw unsupported();
    }

    @Override
    public BlockPos getHeightmapPos(Heightmap.Types heightmap, BlockPos pos) {
        throw unsupported();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        throw unsupported();
    }

    @Override
    public MinecraftServer getServer() {
        throw unsupported();
    }

    @Override
    public RandomSource getRandom() {
        throw unsupported();
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        throw unsupported();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        throw unsupported();
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean requireChunk) {
        throw unsupported();
    }

    @Override
    public ChunkSource getChunkSource() {
        throw unsupported();
    }

    @Override
    public LevelLightEngine getLightEngine() {
        throw unsupported();
    }

    @Override
    public LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks() {
        throw unsupported();
    }

    @Override
    public LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks() {
        throw unsupported();
    }

    @Override
    public BiomeManager getBiomeManager() {
        throw unsupported();
    }

    @Override
    public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
        throw unsupported();
    }

    @Override
    public LevelData getLevelData() {
        throw unsupported();
    }

    /** Data holder with inert values; no recipe-cache path reads it. */
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
            throw unsupported();
        }

        @Override
        public Difficulty getDifficulty() {
            throw unsupported();
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
