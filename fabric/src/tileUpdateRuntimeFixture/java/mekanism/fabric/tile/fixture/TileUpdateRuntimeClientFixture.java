package mekanism.fabric.tile.fixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import mekanism.common.tile.base.TileEntityUpdateable;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Isolated real-client probe for the production reduced-tile update lifecycle. It observes the fixture tile that production code creates on the logical client from
 * the server's reduced tags and asserts that each of the three dispatch paths applied the reduced value <em>without</em> running the fixture's full reader or
 * clearing its save-only sentinel. After the server drops the connection it reconnects, proving the custom payload resolves the current owner world rather than a
 * retained one.
 */
public final class TileUpdateRuntimeClientFixture implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/TileUpdateRuntimeClientFixture");
    private static final Path ROOT = Path.of(System.getProperty("mekanism.tile.runtimeRoot", "run/tile-update-runtime")).toAbsolutePath().normalize();
    private static final String ADDRESS = System.getProperty("mekanism.tile.address", "127.0.0.1:25570");
    private static final BlockPos TILE_POS = new BlockPos(0, 100, 0);

    /** Bounded wait budget in client ticks, so a missing update fails instead of passing on stale state. */
    private static final int WAIT_TICKS = 200;
    private static int stage;
    private static int reducedLoadsAtJoin;
    private static int dataPacketCallsAtJoin;
    private static int waitTicks;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(TileUpdateRuntimeClientFixture::tick);
    }

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null) {
            if (stage == 3 && Files.isRegularFile(ROOT.resolve("reconnect.signal"))) {
                consume("reconnect.signal");
                nextStage(4);
                LOGGER.info("MEKANISM_TILE_RUNTIME_CLIENT phase=reconnecting");
                connect(client);
            }
            return;
        }
        TileUpdateRuntimeFixture tile = tile(level);
        if (tile == null) {
            return;
        }
        if (stage == 0) {
            //Path one: the initial chunk packet must arrive already reduced, with the save-only sentinel untouched
            require(client.getConnection() != null, "the initial chunk proof must run on a live connection");
            require(client.getConnection().registryAccess() == level.registryAccess(),
                  "the client must resolve the connection's registry access rather than a retained one");
            require(tile.reduced() == 3, "the initial chunk reduced update must carry the server reduced value 3 but saw " + tile);
            require(tile.reducedLoads() >= 1, "the initial chunk path must run the reduced handler at least once");
            require(tile.saveOnly() == 0, "the initial reduced update must not invent a save-only value");
            require(tile.fullLoads() == 0, "the initial chunk path must never run the fixture full reader, but ran it " + tile.fullLoads() + " times");
            reducedLoadsAtJoin = tile.reducedLoads();
            dataPacketCallsAtJoin = tile.dataPacketCalls();
            require(dataPacketCallsAtJoin == 0, "the initial chunk path must not run the vanilla block-entity packet entry point");
            LOGGER.info("MEKANISM_TILE_RUNTIME_CLIENT phase=initial_chunk reduced=3 full_loads=0 reduced_loads={} save_only=0",
                  reducedLoadsAtJoin);
            marker("client-chunk.complete");
            nextStage(1);
            return;
        }
        if (stage == 1) {
            //Path two: the vanilla block entity data packet must dispatch through onDataPacket into the reduced handler. The packet needs a network round trip, so
            //wait for the value under a bounded tick budget instead of asserting on the first observation.
            if (tile.reduced() != 11 && !expired()) {
                return;
            }
            require(tile.reduced() == 11, "the vanilla block entity packet must apply the reduced value 11 but saw " + tile);
            require(tile.fullLoads() == 0, "the vanilla packet path must not run the fixture full reader, but ran it " + tile.fullLoads() + " times");
            require(tile.saveOnly() == 0, "the vanilla packet path must not clear the save-only sentinel");
            require(tile.reducedLoads() > reducedLoadsAtJoin, "the vanilla packet path must reach the reduced handler");
            require(tile.dataPacketCalls() > dataPacketCallsAtJoin,
                  "the vanilla block-entity packet path must dispatch through onDataPacket rather than bypassing it");
            dataPacketCallsAtJoin = tile.dataPacketCalls();
            LOGGER.info("MEKANISM_TILE_RUNTIME_CLIENT phase=vanilla_packet reduced=11 full_loads={} reduced_loads={} on_data_packet={}",
                  tile.fullLoads(), tile.reducedLoads(), tile.dataPacketCalls());
            marker("client-vanilla.complete");
            nextStage(2);
            return;
        }
        if (stage == 2) {
            //Path three: our own reduced-update payload through Fabric play S2C, again under a bounded wait
            if (tile.reduced() != 22 && !expired()) {
                return;
            }
            require(tile.reduced() == 22, "the custom payload must apply the reduced value 22 but saw " + tile);
            require(tile.fullLoads() == 0, "the custom payload path must not run the fixture full reader, but ran it " + tile.fullLoads() + " times");
            require(tile.saveOnly() == 0, "the custom payload path must not clear the save-only sentinel");
            require(tile.dataPacketCalls() == dataPacketCallsAtJoin,
                  "the custom payload path must use its own receiver rather than the vanilla onDataPacket entry point");
            LOGGER.info("MEKANISM_TILE_RUNTIME_CLIENT phase=custom_payload reduced=22 full_loads={} reduced_loads={} on_data_packet={}",
                  tile.fullLoads(), tile.reducedLoads(), tile.dataPacketCalls());
            marker("client-custom.complete");
            marker("client-reconnect-request.complete");
            nextStage(3);
            return;
        }
        if (stage == 4) {
            //After the real reconnect this is a fresh client level: the initial chunk reduced update must have delivered the current server value without a full read
            if (tile.reduced() != 22 && !expired()) {
                return;
            }
            require(client.getConnection() != null, "the reconnect proof must run on a live connection");
            require(tile.reduced() == 22, "a fresh connection's initial chunk update must carry the current server value but saw " + tile);
            require(tile.fullLoads() == 0, "after reconnect the reduced path must still not run the fixture full reader");
            require(tile.saveOnly() == 0, "after reconnect the reduced path must still not invent a save-only value");
            LOGGER.info("MEKANISM_TILE_RUNTIME_CLIENT phase=reconnect reduced={} full_loads={} owner_world_current=true",
                  tile.reduced(), tile.fullLoads());
            marker("client-reconnect.complete");
            nextStage(5);
            return;
        }
        if (stage == 5) {
            //The custom payload must still resolve the current owner world after the reconnect
            if (tile.reduced() != 33 && !expired()) {
                return;
            }
            require(tile.reduced() == 33, "the post-reconnect custom payload must apply to the current world's tile but saw " + tile);
            require(tile.fullLoads() == 0, "the post-reconnect custom payload must not run the fixture full reader");
            require(tile.saveOnly() == 0, "the post-reconnect custom payload must not invent a save-only value");
            LOGGER.info("MEKANISM_TILE_RUNTIME_CLIENT phase=reconnect_payload reduced=33 full_loads=0 owner_world_current=true");
            marker("client-reconnect-payload.complete");
            nextStage(6);
        }
    }

    private static void nextStage(int next) {
        stage = next;
        waitTicks = 0;
    }

    /** Returns true once the bounded wait budget for the current stage is exhausted. */
    private static boolean expired() {
        if (++waitTicks > WAIT_TICKS) {
            throw new IllegalStateException("timed out waiting for a reduced update in stage " + stage);
        }
        return false;
    }

    private static TileUpdateRuntimeFixture tile(ClientLevel level) {
        BlockEntity blockEntity = level.getBlockEntity(TILE_POS);
        if (blockEntity instanceof TileUpdateRuntimeFixture fixture) {
            return fixture;
        }
        if (blockEntity != null && !(blockEntity instanceof TileEntityUpdateable)) {
            require(false, "the fixture position must hold the production base subclass but held " + blockEntity);
        }
        return null;
    }

    private static void consume(String name) {
        try {
            Files.deleteIfExists(ROOT.resolve(name));
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to consume tile runtime signal " + name, failure);
        }
    }

    private static void connect(Minecraft client) {
        ServerData data = new ServerData("Mekanism tile update runtime", ADDRESS, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(ADDRESS), data, true, null);
    }

    private static void marker(String name) {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(ROOT.resolve(name), "ok\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to write tile runtime client marker " + name, failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
