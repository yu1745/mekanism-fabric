package mekanism.fabric.tile.fixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import mekanism.fabric.network.FabricTileUpdateNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Isolated real-server probe for the production reduced-tile update lifecycle. It registers a fixture-only block/entity type over the real production base and drives
 * the three real client dispatch paths: the initial chunk tag, the vanilla {@link ClientboundBlockEntityDataPacket} path, and the custom reduced-update payload.
 */
public final class TileUpdateRuntimeServerFixture implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/TileUpdateRuntimeServerFixture");
    private static final Path ROOT = Path.of(System.getProperty("mekanism.tile.runtimeRoot", "run/tile-update-runtime")).toAbsolutePath().normalize();
    private static final BlockPos TILE_POS = new BlockPos(0, 100, 0);
    private static final BlockPos VANILLA_POS = new BlockPos(3, 100, 0);
    private static final ResourceLocation BLOCK_ID = ResourceLocation.fromNamespaceAndPath("mekanism_tile_fixture", "update_probe");
    private static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath("mekanism_tile_fixture", "update_probe");

    private static BlockEntityType<TileUpdateRuntimeFixture> fixtureType;
    private static int stage;

    private static net.minecraft.core.Holder<BlockEntityType<TileUpdateRuntimeFixture>> typeHolder() {
        BlockEntityType<TileUpdateRuntimeFixture> type = fixtureType;
        if (type == null) {
            throw new IllegalStateException("the fixture block entity type must be registered before any tile is created");
        }
        return net.minecraft.core.Holder.direct(type);
    }
    private static ServerPlayer probePlayer;

    @Override
    public void onInitialize() {
        Block fixtureBlock = Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, BLOCK_ID),
              new FixtureBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)));
        //The block entity factory reads the type holder lazily, because registration has not returned it yet
        fixtureType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, TYPE_ID),
              FabricBlockEntityTypeBuilder.create((pos, state) -> new TileUpdateRuntimeFixture(typeHolder(), pos, state), fixtureBlock).build());
        ServerLifecycleEvents.SERVER_STARTED.register(TileUpdateRuntimeServerFixture::prepareWorld);
        ServerTickEvents.END_SERVER_TICK.register(TileUpdateRuntimeServerFixture::tick);
    }

    private static void prepareWorld(MinecraftServer server) {
        ServerLevel level = server.overworld();
        //Place the fixture block and the plain vanilla control block, then configure the real production tile
        level.setBlockAndUpdate(TILE_POS, BuiltInRegistries.BLOCK.get(BLOCK_ID).defaultBlockState());
        level.setBlockAndUpdate(VANILLA_POS, Blocks.CHEST.defaultBlockState());
        TileUpdateRuntimeFixture tile = requireTile(level);
        tile.setSaveOnly(7);
        tile.setReduced(3);
        //Persisted/full-load restoration is demonstrated separately from the reduced paths, on a detached instance of the same production subclass
        net.minecraft.nbt.CompoundTag persisted = tile.saveWithFullMetadata(level.registryAccess());
        TileUpdateRuntimeFixture restored = new TileUpdateRuntimeFixture(typeHolder(), TILE_POS, fixtureBlockState());
        restored.loadWithComponents(persisted, level.registryAccess());
        require(restored.fullLoads() == 1, "a full persisted load must run the fixture full reader exactly once");
        require(restored.saveOnly() == 7, "a full persisted load must restore the save-only sentinel");
        require(restored.reduced() == 3, "a full persisted load must restore the reduced value too");
        marker("server-prepared.complete");
        LOGGER.info("MEKANISM_TILE_RUNTIME_SERVER phase=prepared tile={} full_restore=true save_only_restored=true", TILE_POS);
    }

    private static void tick(MinecraftServer server) {
        if (stage == 0) {
            ServerLevel level = server.overworld();
            if (level.getBlockEntity(TILE_POS) == null) {
                return;
            }
            if (server.getPlayerList().getPlayers().isEmpty()) {
                return;
            }
            probePlayer = server.getPlayerList().getPlayers().getFirst();
            //Spawn is far from the fixture chunk, so move the probe next to the tile; tracking then covers the fixture chunk on a later tick
            probePlayer.teleportTo(level, TILE_POS.getX() + 0.5D, TILE_POS.getY() + 1.0D, TILE_POS.getZ() + 0.5D, 0.0F, 0.0F);
            LOGGER.info("MEKANISM_TILE_RUNTIME_SERVER phase=player_teleported player={}", probePlayer.getName().getString());
            stage = 1;
            return;
        }
        ServerLevel level = server.overworld();
        TileUpdateRuntimeFixture tile = requireTile(level);
        if (stage == 1 && !Files.isRegularFile(ROOT.resolve("server-player-joined.complete"))) {
            //Wait for the real chunk tracking to include the fixture chunk after the teleport
            if (FabricTileUpdateNetworking.tracking(level, TILE_POS).isEmpty()) {
                return;
            }
            marker("server-player-joined.complete");
            LOGGER.info("MEKANISM_TILE_RUNTIME_SERVER phase=player_joined player={} tracking={}",
                  probePlayer.getName().getString(), FabricTileUpdateNetworking.tracking(level, TILE_POS).size());
            return;
        }
        if (stage == 1 && Files.isRegularFile(ROOT.resolve("client-chunk.complete"))) {
            //Path two: the vanilla block entity data packet, which carries the same reduced tag through onDataPacket
            require(tile.reduced() == 3, "the initial chunk path must leave the server-side reduced value unchanged");
            probePlayer.connection.send(ClientboundBlockEntityDataPacket.create(tile,
                  (blockEntity, registries) -> new net.minecraft.nbt.CompoundTag()));
            probePlayer.connection.send(ClientboundBlockEntityDataPacket.create(tile));
            tile.setReduced(11);
            //A second packet with the new value; the client must observe 11 without running the full reader
            probePlayer.connection.send(ClientboundBlockEntityDataPacket.create(tile));
            //The plain vanilla control block entity keeps its ordinary handling
            BlockEntity chest = level.getBlockEntity(VANILLA_POS);
            require(chest instanceof net.minecraft.world.level.block.entity.ChestBlockEntity, "the control block entity must be a vanilla chest");
            net.minecraft.nbt.CompoundTag chestData = new net.minecraft.nbt.CompoundTag();
            chestData.putString("CustomName", "\"tile-runtime-vanilla-control\"");
            chest.loadCustomOnly(chestData, level.registryAccess());
            probePlayer.connection.send(ClientboundBlockEntityDataPacket.create(chest,
                  (blockEntity, registries) -> blockEntity.saveWithoutMetadata(registries)));
            LOGGER.info("MEKANISM_TILE_RUNTIME_SERVER phase=vanilla_packet_sent reduced=11");
            stage = 2;
            return;
        }
        if (stage == 2 && Files.isRegularFile(ROOT.resolve("client-vanilla.complete"))) {
            //Path three: our own reduced-update payload through Fabric play S2C
            tile.setReduced(22);
            tile.sendUpdatePacket();
            LOGGER.info("MEKANISM_TILE_RUNTIME_SERVER phase=custom_payload_sent reduced=22 tracking={}",
                  FabricTileUpdateNetworking.tracking(level, TILE_POS).size());
            stage = 3;
            return;
        }
        if (stage == 3 && Files.isRegularFile(ROOT.resolve("client-custom.complete"))) {
            //Force a fresh connection so the reduced updates must resolve the current owner rather than a retained world
            probePlayer.connection.disconnect(net.minecraft.network.chat.Component.literal("Tile update runtime reconnect probe"));
            marker("server-disconnected.complete");
            LOGGER.info("MEKANISM_TILE_RUNTIME_SERVER phase=disconnected");
            stage = 4;
            return;
        }
        if (stage == 4 && Files.isRegularFile(ROOT.resolve("client-reconnect.complete"))) {
            //The fresh connection must resolve the current owner world: the reduced state the client now holds came from this live tile's initial chunk tag
            require(tile.reduced() == 22, "the server tile must still hold the pre-reconnect reduced value after the client reconnected");
            tile.setReduced(33);
            tile.sendUpdatePacket();
            LOGGER.info("MEKANISM_TILE_RUNTIME_SERVER phase=reconnect_payload_sent reduced=33 tracking={}",
                  FabricTileUpdateNetworking.tracking(level, TILE_POS).size());
            stage = 5;
            return;
        }
        if (stage == 5 && Files.isRegularFile(ROOT.resolve("client-reconnect-payload.complete"))) {
            marker("server-reconnect.complete");
            LOGGER.info("MEKANISM_TILE_RUNTIME_SERVER phase=reconnect_verified reduced={} full_loads={}", tile.reduced(), tile.fullLoads());
            stage = 6;
        }
    }

    private static BlockState fixtureBlockState() {
        return BuiltInRegistries.BLOCK.get(BLOCK_ID).defaultBlockState();
    }

    private static TileUpdateRuntimeFixture requireTile(ServerLevel level) {
        BlockEntity tile = level.getBlockEntity(TILE_POS);
        require(tile instanceof TileUpdateRuntimeFixture, "the fixture block entity must exist at " + TILE_POS + " but was " + tile);
        return (TileUpdateRuntimeFixture) tile;
    }

    static void marker(String name) {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(ROOT.resolve(name), "ok\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to write tile runtime marker " + name, failure);
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    /** Fixture-only block whose only job is to carry the fixture block entity. */
    private static final class FixtureBlock extends Block implements EntityBlock {

        private FixtureBlock(Properties properties) {
            super(properties);
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TileUpdateRuntimeFixture(typeHolder(), pos, state);
        }
    }
}
