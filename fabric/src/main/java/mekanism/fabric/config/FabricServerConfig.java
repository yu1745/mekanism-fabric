package mekanism.fabric.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import mekanism.api.radiation.RadiationValidation;
import mekanism.common.config.ChemicalConfigAccess;
import mekanism.common.config.IMekanismConfig;
import mekanism.common.config.MekanismConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** World and connection owner for the admitted general, gear, machine storage/usage and tier SERVER configs. */
public final class FabricServerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricServerConfig");
    private static final List<IMekanismConfig> CONFIGS = new ArrayList<>(List.of(MekanismConfig.general, MekanismConfig.gear, MekanismConfig.storage,
          MekanismConfig.usage, MekanismConfig.tiers));
    private static MinecraftServer owner;
    private static FabricMekanismConfigManager manager;
    private static Connection clientOwner;
    private static boolean clientSharesServer;
    private static boolean initialized;

    private FabricServerConfig() {
    }

    /** Add an actual Mekanism addon SERVER schema before any world or client owns the config list. */
    public static synchronized void registerModuleConfig(IMekanismConfig config) {
        if (owner != null || clientOwner != null || config.getConfigType() != mekanism.common.config.MekanismConfigType.SERVER
              || CONFIGS.stream().anyMatch(existing -> existing == config || existing.getFileName().equals(config.getFileName()))) {
            throw new IllegalStateException("Cannot register module server config " + config.getFileName());
        }
        CONFIGS.add(config);
    }

    public static synchronized void initializeCallbacks() {
        if (initialized) return;
        ServerLifecycleEvents.SERVER_STARTING.register(FabricServerConfig::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(FabricServerConfig::stop);
        initialized = true;
    }

    private static synchronized void start(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Server config must load on the server thread");
        if (owner != null || CONFIGS.stream().anyMatch(IMekanismConfig::isLoaded)) {
            throw new IllegalStateException("Canonical server config already has an owner");
        }
        Path root = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").toAbsolutePath().normalize();
        FabricMekanismConfigManager candidate = FabricMekanismConfigManager.createForServer(root);
        try {
            CONFIGS.forEach(candidate::registerAndLoad);
        } catch (RuntimeException | Error failure) {
            try {
                candidate.unloadAll();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        var general = MekanismConfig.general;
        ChemicalConfigAccess.publishServerPolicy(general);
        RadiationValidation.publish(ChemicalConfigAccess::radiationEnabled);
        manager = candidate;
        owner = server;
        candidate.beginWatching(server::execute);
        //The per-schema counts make the aggregate value count auditable: a dropped or duplicated schema shows up here.
        StringBuilder detail = new StringBuilder();
        CONFIGS.stream().sorted(java.util.Comparator.comparing(IMekanismConfig::getFileName)).forEach(config -> {
            if (!detail.isEmpty()) {
                detail.append(',');
            }
            detail.append(config.getFileName()).append(':').append(config.getConfigSpec().serverSnapshot().size());
        });
        LOGGER.info("MEKANISM_FABRIC_SERVER_CONFIG_PROOF phase=server_start loaded=true configs={} values={} detail={} base_path={} override_path={}",
              CONFIGS.size(), snapshot().size(), detail, FabricLoader.getInstance().getConfigDir(), root);
    }

    private static synchronized void stop(MinecraftServer server) {
        if (owner != server) return;
        if (!server.isSameThread()) throw new IllegalStateException("Server config must unload on the server thread");
        try {
            manager.unloadAll();
        } finally {
            owner = null;
            manager = null;
            ChemicalConfigAccess.resetServerPolicy();
            RadiationValidation.reset();
            LOGGER.info("MEKANISM_FABRIC_SERVER_CONFIG_PROOF phase=server_stop loaded={}", CONFIGS.stream().anyMatch(IMekanismConfig::isLoaded));
        }
    }

    /** Filename prefixes retain each official schema's field paths without collisions. */
    public static synchronized Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (IMekanismConfig config : CONFIGS) {
            config.getConfigSpec().serverSnapshot().forEach((path, value) -> snapshot.put(config.getFileName() + "." + path, value));
        }
        return Map.copyOf(snapshot);
    }

    /** Publish a remote connection's snapshot, or verify the shared integrated-server instance without mutating it. */
    public static synchronized void attachClient(Connection connection, MinecraftServer integratedServer, Map<String, Object> snapshot) {
        Objects.requireNonNull(connection);
        Objects.requireNonNull(snapshot);
        if (clientOwner == connection) throw new IllegalStateException("Duplicate server config snapshot for connection");
        if (clientOwner != null) detachClient(clientOwner);
        if (owner != null) {
            if (owner != integratedServer || !snapshot().equals(snapshot)) {
                throw new IllegalStateException("Integrated server config does not match snapshot");
            }
            clientOwner = connection;
            clientSharesServer = true;
            return;
        }
        Map<String, Object> remaining = new LinkedHashMap<>(snapshot);
        List<IMekanismConfig> installed = new ArrayList<>();
        try {
            for (IMekanismConfig config : CONFIGS) {
                String prefix = config.getFileName() + ".";
                Map<String, Object> values = new LinkedHashMap<>();
                remaining.entrySet().removeIf(entry -> {
                    if (!entry.getKey().startsWith(prefix)) return false;
                    values.put(entry.getKey().substring(prefix.length()), entry.getValue());
                    return true;
                });
                if (config == MekanismConfig.general) {
                    //Official alphaWarning exists only in development; all other fields retain exact schema validation.
                    Object alphaWarning = values.remove("alphaWarning");
                    if (alphaWarning != null && !(alphaWarning instanceof Boolean)) {
                        throw new IllegalArgumentException("Invalid general.alphaWarning");
                    }
                    if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
                        values.put("alphaWarning", alphaWarning == null ? true : alphaWarning);
                    }
                }
                config.getConfigSpec().installRemoteValues(values);
                installed.add(config);
            }
            if (!remaining.isEmpty()) throw new IllegalArgumentException("Unknown server config paths: " + remaining.keySet());
            ChemicalConfigAccess.publishServerPolicy(MekanismConfig.general);
            RadiationValidation.publish(ChemicalConfigAccess::radiationEnabled);
            CONFIGS.forEach(config -> config.clearCache(false));
        } catch (RuntimeException | Error failure) {
            try {
                resetRemote(installed);
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        clientOwner = connection;
        clientSharesServer = false;
    }

    /** Ignore stale disconnects; a local client's disconnect must not unload its integrated server. */
    public static synchronized void detachClient(Connection connection) {
        if (clientOwner != connection) return;
        boolean sharesServer = clientSharesServer;
        clientOwner = null;
        clientSharesServer = false;
        if (!sharesServer) resetRemote(CONFIGS);
    }

    private static void resetRemote(List<IMekanismConfig> configs) {
        RadiationValidation.reset();
        ChemicalConfigAccess.resetServerPolicy();
        RuntimeException firstFailure = null;
        for (IMekanismConfig config : configs) {
            var spec = config.getConfigSpec();
            try {
                spec.resetRemoteValues();
                config.clearCache(false);
            } catch (RuntimeException failure) {
                if (firstFailure == null) firstFailure = failure;
                else firstFailure.addSuppressed(failure);
            } finally {
                spec.unload();
            }
        }
        if (firstFailure != null) throw firstFailure;
    }

    /** Distinguishes an integrated server's shared instance from a remote client's temporary snapshot. */
    public static synchronized boolean isOwner(MinecraftServer server) {
        return owner == server && owner != null;
    }
}
