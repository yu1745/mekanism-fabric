package mekanism.common.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.Chunk3D;
import mekanism.common.network.to_client.PacketUpdateTile;
import mekanism.common.tile.base.TileEntityUpdateable;
import mekanism.test.TileLevelStub;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Focused contract tests for the base-tile persistence and reduced-update owner. Each method binds one risk the compiler cannot see: the reduced-versus-full
 * separation, non-loading dirty coalescing, coordinate caching, the payload's own codec plus recipient ownership, and holder covariance for a real bound deferred
 * registry object. Vanilla {@code BlockEntity} save/load and JDK behavior are deliberately not re-tested.
 */
class TileEntityUpdateableLifecycleTest {

    /**
     * Real registered vanilla block entity type token used by every probe tile. The lifecycle under test never creates a tile through the type, and a fresh
     * {@code BlockEntityType} cannot be constructed after bootstrap because its constructor takes an intrusive holder from an already frozen registry.
     */
    @Nullable
    private static Holder<BlockEntityType<?>> fixtureType;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        fixtureType = Holder.direct(BlockEntityType.FURNACE);
    }

    private static Holder<? extends BlockEntityType<?>> fixtureType() {
        Holder<BlockEntityType<?>> type = fixtureType;
        if (type == null) {
            throw new IllegalStateException("bootstrap must run first");
        }
        return type;
    }

    /**
     * A subclass whose full NBT reader is distinguishable from the base reduced reader: it owns a save-only sentinel and a reduced value. If the reduced path ever ran
     * the virtual {@code loadAdditional}, either the sentinel would move or the full-reader counter would advance.
     */
    static class ProbeTile extends TileEntityUpdateable {

        /** State accepted by the vanilla block entity type token the probe tiles are built with. */
        static final BlockState STATE = Blocks.FURNACE.defaultBlockState();
        static final String SAVE_ONLY = "save_only";
        static final String REDUCED = "reduced";

        private int fullLoads;
        private int saveOnly = -1;
        private int reduced = -1;
        private final List<Integer> comparatorCalls = new ArrayList<>();

        ProbeTile(Holder<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        @Override
        public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
            fullLoads++;
            super.loadAdditional(tag, provider);
            saveOnly = tag.getInt(SAVE_ONLY);
        }

        @Override
        public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
            super.saveAdditional(tag, provider);
            tag.putInt(SAVE_ONLY, saveOnly);
            tag.putInt(REDUCED, reduced);
        }

        @Override
        public CompoundTag getReducedUpdateTag(HolderLookup.Provider provider) {
            //Reduced updates intentionally omit the save-only field
            CompoundTag tag = super.getReducedUpdateTag(provider);
            tag.putInt(REDUCED, reduced);
            return tag;
        }

        @Override
        public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
            super.handleUpdateTag(tag, provider);
            reduced = tag.getInt(REDUCED);
        }

        @Override
        public void markDirtyComparator() {
            comparatorCalls.add(1);
        }

        /** Exposes the protected coordinate-cache request for the lifecycle test. */
        void requestCoordCache() {
            cacheCoord();
        }

        int fullLoads() {
            return fullLoads;
        }

        int saveOnly() {
            return saveOnly;
        }

        int reduced() {
            return reduced;
        }

        List<Integer> comparatorCalls() {
            return comparatorCalls;
        }
    }

    /**
     * Applies the exact vanilla {@code BlockEntity.ComponentHelper.COMPONENTS_CODEC} expression to the same tag and requires the base reduced load to agree with it: a
     * present result replaces the components, an error or absent field leaves them untouched. This documents the real codec behavior instead of asserting a guess.
     */
    private static void assertCodecOracle(ProbeTile tile, DataComponentMap before, CompoundTag tag, HolderLookup.Provider provider) {
        java.util.Optional<DataComponentMap> oracle = DataComponentMap.CODEC
              .optionalFieldOf("components", DataComponentMap.EMPTY).codec()
              .parse(provider.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag)
              .resultOrPartial(ignored -> {
              });
        assertEquals(oracle.orElse(before), tile.components(),
              "the reduced load must match the vanilla optional components codec result exactly");
    }

    private static HolderLookup.Provider provider() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static ProbeTile newTile(BlockPos pos) {
        return new ProbeTile(fixtureType(), pos, ProbeTile.STATE);
    }

    @Test
    void reducedLoadReadsOnlyBaseStateAndPreservesSaveOnlyField() {
        HolderLookup.Provider provider = provider();
        ProbeTile source = newTile(BlockPos.ZERO);
        source.saveOnly = 7;
        source.reduced = 3;

        //A full persisted load is the only path allowed to run the subclass reader
        CompoundTag persisted = source.saveWithFullMetadata(provider);
        ProbeTile restored = newTile(BlockPos.ZERO);
        restored.loadWithComponents(persisted, provider);
        assertEquals(1, restored.fullLoads(), "a full persisted load must run the subclass reader exactly once");
        assertEquals(7, restored.saveOnly(), "a full persisted load must restore the save-only field");

        //The reduced packet path must not run the subclass reader and must keep the existing save-only field
        CompoundTag reducedTag = new CompoundTag();
        reducedTag.putInt(ProbeTile.REDUCED, 42);
        restored.handleUpdateTag(reducedTag, provider);
        assertEquals(1, restored.fullLoads(), "a reduced load must not invoke the subclass full reader");
        assertEquals(7, restored.saveOnly(), "a reduced load must not erase the save-only field");
        assertEquals(42, restored.reduced(), "a reduced load must apply the reduced state");
    }

    @Test
    void reducedLoadAppliesVanillaComponentsClearsAbsentOnesAndSurvivesMalformedOnes() {
        HolderLookup.Provider provider = provider();
        ProbeTile tile = newTile(BlockPos.ZERO);

        //The vanilla persistent component form for the custom name is a flat text component value
        CompoundTag withName = new CompoundTag();
        CompoundTag components = new CompoundTag();
        components.putString("minecraft:custom_name", "\"Renamed\"");
        withName.put("components", components);
        tile.handleUpdateTag(withName, provider);
        Component applied = tile.components().get(DataComponents.CUSTOM_NAME);
        assertNotNull(applied, "an explicit component in a reduced tag must be applied");
        assertEquals("Renamed", applied.getString(), "the applied component keeps its content");

        //Malformed and absent inputs must do exactly what the vanilla optional components codec does, including any partial-decode semantics
        CompoundTag malformed = new CompoundTag();
        malformed.putString("components", "not-a-component-map");
        DataComponentMap beforeMalformed = tile.components();
        tile.handleUpdateTag(malformed, provider);
        assertCodecOracle(tile, beforeMalformed, malformed, provider);

        CompoundTag absent = new CompoundTag();
        DataComponentMap beforeAbsent = tile.components();
        tile.handleUpdateTag(absent, provider);
        assertCodecOracle(tile, beforeAbsent, absent, provider);
        assertEquals(DataComponentMap.EMPTY, tile.components(), "an absent components field must clear to the vanilla default");
    }

    @Test
    void dirtyCoalescingNeverCreatesChunksAndSeparatesComparatorFromSaveOnly() {
        TileLevelStub level = TileLevelStub.create();
        level.installChunk(0, 0);
        ProbeTile tile = level.installTile(newTile(BlockPos.ZERO));

        //The official coalescing is per game tick and its initial timestamp is zero, so a mark inside tick zero is deliberately a no-op
        level.clearChunkUnsaved(0, 0);
        int comparatorBeforeZeroTick = tile.comparatorCalls().size();
        tile.setChanged();
        assertFalse(level.isChunkUnsaved(0, 0), "a mark inside the initial zero tick is coalesced away");
        assertEquals(1, tile.comparatorCalls().size() - comparatorBeforeZeroTick,
              "the comparator request is per call, not coalesced with the chunk dirty mark");

        //The official implementation coalesces only the chunk-dirty call inside a tick; the comparator request stays per call
        level.setGameTime(1);
        int lookupsBefore = level.fullChunkLookups();
        tile.setChanged();
        tile.setChanged();
        assertTrue(level.isChunkUnsaved(0, 0), "the loaded chunk must be marked dirty");
        assertEquals(1, level.fullChunkLookups() - lookupsBefore, "repeated same-tick marks must resolve the chunk only once");
        assertEquals(3, tile.comparatorCalls().size(), "each comparator-requesting mark keeps its own comparator request");

        //In the next tick, save-only marking resolves the chunk again without ever touching the comparator path
        level.setGameTime(2);
        level.clearChunkUnsaved(0, 0);
        lookupsBefore = level.fullChunkLookups();
        tile.markForSave();
        assertTrue(level.isChunkUnsaved(0, 0), "save-only marking must still mark the chunk dirty");
        assertEquals(1, level.fullChunkLookups() - lookupsBefore, "a new tick must allow one more chunk dirty mark");
        assertEquals(3, tile.comparatorCalls().size(), "save-only marking must never update comparators");

        //An unloaded position must neither create a chunk nor report success
        level.setGameTime(3);
        ProbeTile unloaded = level.installTile(newTile(new BlockPos(4096, 0, 4096)));
        assertFalse(level.hasChunkInstalled(256, 256), "no chunk must exist for the unloaded position");
        //The unloaded position resolves no chunk and must not create one; the stub fails closed if anything asks for creation
        unloaded.markForSave();
        assertFalse(level.hasChunkInstalled(256, 256), "marking an unloaded position dirty must never create a chunk");
        assertFalse(level.isLoaded(new BlockPos(4096, 0, 4096)), "the unloaded position must stay unloaded");
    }

    @Test
    void coordinateCacheTracksLevelAssignmentDimensionChangeAndPersistedLoad() {
        HolderLookup.Provider provider = provider();
        BlockPos pos = new BlockPos(-33, 5, -17);
        ProbeTile tile = newTile(pos);
        tile.requestCoordCache();

        TileLevelStub level = TileLevelStub.create();
        tile.setLevel(level);
        assertEquals(GlobalPos.of(level.dimension(), pos), tile.getTileGlobalPos(),
              "the cached coordinate must follow the assigned level and the exact negative position");
        assertEquals(new Chunk3D(level.dimension(), -3, -2), tile.getTileChunk(),
              "the cached chunk must use the negative-coordinate section conversion");

        //A real dimension change must be invalidated through the level assignment
        TileLevelStub other = TileLevelStub.create();
        tile.setLevel(other);
        assertEquals(GlobalPos.of(other.dimension(), pos), tile.getTileGlobalPos(),
              "reassigning the level must refresh the cached coordinate");
        assertEquals(new Chunk3D(other.dimension(), -3, -2), tile.getTileChunk(), "the cached chunk must follow the new dimension");

        //And a persisted load must refresh it as well
        ProbeTile reloaded = level.installTile(newTile(pos));
        reloaded.requestCoordCache();
        reloaded.loadAdditional(new CompoundTag(), provider);
        assertEquals(new Chunk3D(level.dimension(), -3, -2), reloaded.getTileChunk(), "a persisted load must refresh the cached coordinate");
    }

    @Test
    void payloadCodecAndDispatchStayBoundToTheAddressedOwnersOnly() {
        HolderLookup.Provider provider = provider();
        TileLevelStub server = TileLevelStub.create();
        server.installChunk(0, 0);
        ProbeTile tile = server.installTile(newTile(new BlockPos(1, 64, 2)));
        tile.reduced = 9;

        PacketUpdateTile payload = new PacketUpdateTile(tile);
        assertEquals(new BlockPos(1, 64, 2), payload.pos(), "the payload must carry the tile position");
        assertEquals(9, payload.updateTag().getInt(ProbeTile.REDUCED), "the payload must carry the reduced tag");
        assertEquals(ResourceLocation.fromNamespaceAndPath("mekanism", "update_tile"), PacketUpdateTile.TYPE.id(),
              "the payload identifier must stay mekanism:update_tile");

        io.netty.buffer.ByteBuf buffer = Unpooled.buffer();
        PacketUpdateTile.STREAM_CODEC.encode(buffer, payload);
        PacketUpdateTile decoded = PacketUpdateTile.STREAM_CODEC.decode(buffer);
        assertEquals(payload, decoded, "the payload must round-trip through its own stream codec");

        //Dispatch only touches the tile at the payload position
        TileLevelStub client = TileLevelStub.create();
        client.setRemote(true);
        client.installChunk(0, 0);
        ProbeTile receiver = client.installTile(newTile(new BlockPos(1, 64, 2)));
        ProbeTile bystander = client.installTile(newTile(new BlockPos(5, 64, 2)));
        decoded.handle(client, provider);
        assertEquals(9, receiver.reduced(), "dispatch must apply the reduced tag to the addressed tile");
        assertEquals(-1, bystander.reduced(), "dispatch must not mutate another tile");

        //An unloaded position must be skipped without creating the chunk
        client.removeTile(receiver);
        new PacketUpdateTile(new BlockPos(4096, 5, 4096), new CompoundTag()).handle(client, provider);
        assertFalse(client.hasChunkInstalled(256, 256), "a packet for an unloaded position must not create a chunk");

        //The stub still fails closed rather than inventing chunks for a normal level lookup
        assertThrows(IllegalStateException.class, () -> client.getChunk(64, 64, ChunkStatus.FULL, true),
              "the level stub must fail closed instead of creating a chunk");
    }

    @Test
    void deferredHolderConstructorIsCovariantAndResolvesTheBoundType() {
        BlockEntityType<ProbeTile> canonical = castFurnace();
        MekanismDeferredRegister<BlockEntityType<?>> register = new MekanismDeferredRegister<>(Registries.BLOCK_ENTITY_TYPE, "mekanism_test");
        //The official base receives exactly this two-parameter deferred registry object shape; the vanilla Holder supertype makes it covariant
        MekanismDeferredHolder<BlockEntityType<?>, BlockEntityType<?>> holder = register.register("updateable_probe", () -> canonical);
        register.registerAll((registryKey, key, value) -> value);

        assertTrue(holder.isBound(), "the deferred holder must be bound after registration");
        assertSame(canonical, holder.value(), "the holder must resolve the exact registered value");
        assertSame(holder.getKey(), holder.key(), "the holder must expose its stable key");

        ProbeTile tile = new ProbeTile(holder, new BlockPos(0, 0, 0), ProbeTile.STATE);
        assertSame(canonical, tile.getType(), "the base constructor must hand the resolved vanilla block entity type to BlockEntity");
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<ProbeTile> castFurnace() {
        return (BlockEntityType<ProbeTile>) (BlockEntityType<?>) BlockEntityType.FURNACE;
    }
}
