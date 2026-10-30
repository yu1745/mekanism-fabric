package mekanism.fabric.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import mekanism.common.config.MekanismConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns the canonical SERVER gear config for one actual server world at a time. */
public final class FabricGearConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricGearConfig");
    private static MinecraftServer owner;
    private static FabricMekanismConfigManager manager;
    private static Connection clientOwner;
    private static boolean clientSharesServer;
    private static boolean initialized;

    private FabricGearConfig() {
    }

    public static synchronized void initializeCallbacks() {
        if (initialized) return;
        ServerLifecycleEvents.SERVER_STARTING.register(FabricGearConfig::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(FabricGearConfig::stop);
        initialized = true;
    }

    private static synchronized void start(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Gear config must load on the server thread");
        if (owner != null || MekanismConfig.gear.isLoaded()) {
            throw new IllegalStateException("Canonical gear config already has an owner");
        }
        Path root = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").toAbsolutePath().normalize();
        FabricMekanismConfigManager candidate = new FabricMekanismConfigManager(root);
        candidate.registerAndLoad(MekanismConfig.gear);
        manager = candidate;
        owner = server;
        LOGGER.info("MEKANISM_FABRIC_GEAR_CONFIG_PROOF phase=server_start loaded=true path={}", root.resolve("Mekanism/gear.toml"));
    }

    private static synchronized void stop(MinecraftServer server) {
        if (owner != server) return;
        if (!server.isSameThread()) throw new IllegalStateException("Gear config must unload on the server thread");
        try {
            manager.unregister(MekanismConfig.gear);
        } finally {
            owner = null;
            manager = null;
            LOGGER.info("MEKANISM_FABRIC_GEAR_CONFIG_PROOF phase=server_stop loaded={}", MekanismConfig.gear.isLoaded());
        }
    }

    /** Publish a remote connection's snapshot, or verify the shared integrated-server instance without mutating it. */
    public static synchronized void attachClient(Connection connection, MinecraftServer integratedServer, Map<String, Object> snapshot) {
        Objects.requireNonNull(connection);
        Objects.requireNonNull(snapshot);
        if (clientOwner == connection) throw new IllegalStateException("Duplicate gear config snapshot for connection");
        if (clientOwner != null) detachClient(clientOwner);
        if (owner != null) {
            if (owner != integratedServer || !MekanismConfig.gear.getConfigSpec().scalarSnapshot().equals(snapshot)) {
                throw new IllegalStateException("Integrated server gear config does not match snapshot");
            }
            clientOwner = connection;
            clientSharesServer = true;
            return;
        }
        var spec = MekanismConfig.gear.getConfigSpec();
        spec.installRemoteScalars(snapshot);
        clientOwner = connection;
        clientSharesServer = false;
        try {
            MekanismConfig.gear.clearCache(false);
        } catch (RuntimeException | Error failure) {
            try {
                detachClient(connection);
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /** Ignore stale disconnects; a local client's disconnect must not unload its integrated server. */
    public static synchronized void detachClient(Connection connection) {
        if (clientOwner != connection) return;
        boolean sharesServer = clientSharesServer;
        clientOwner = null;
        clientSharesServer = false;
        if (!sharesServer) {
            var spec = MekanismConfig.gear.getConfigSpec();
            try {
                spec.resetRemoteScalars();
                MekanismConfig.gear.clearCache(false);
            } finally {
                spec.unload();
            }
        }
    }

    /** Distinguishes an integrated server's shared instance from a remote client's temporary snapshot. */
    public static synchronized boolean isOwner(MinecraftServer server) {
        return owner == server && owner != null;
    }
}
