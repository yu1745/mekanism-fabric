package mekanism.fabric.worldgen;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import mekanism.common.config.MekanismConfig;
import mekanism.fabric.config.FabricMekanismConfigManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Per-world SERVER config owner for {@code serverconfig/Mekanism/world.toml} ({@link MekanismConfig#world}).
 *
 * <p>Official NeoForge loads every SERVER-type config under the world's {@code serverconfig} directory when the server
 * starts and unloads it on stop; the worldgen feature codecs ({@code mekanism:ore}, {@code mekanism:disk}, the
 * configurable providers and the {@code mekanism:disableable} placement filter) resolve their vein/salt values lazily
 * through the cached config values, so this owner must be live before the first chunk decorates and released on stop.
 * The lifecycle mirrors {@code FabricChemicalConfig}'s preserving world fragment: load on SERVER_STARTING on the server
 * thread, unload on SERVER_STOPPED, one claim per world path.</p>
 *
 * <p>Integration note for the future full-config closure: {@link MekanismConfig#registerConfigs} must not register the
 * world config through the global {@code config/} rooted manager, or the two owners would fight over the same spec -
 * the world config's real file location is always the per-world {@code serverconfig/Mekanism/world.toml}.</p>
 */
public final class FabricWorldGenConfig {
    private record OwnedServer(FabricMekanismConfigManager manager, Path configRoot) {
    }

    private static final Map<MinecraftServer, OwnedServer> SERVERS = new IdentityHashMap<>();
    private static final Set<Path> CLAIMS = new HashSet<>();
    private static boolean callbacksInitialized;

    private FabricWorldGenConfig() {
    }

    public static synchronized void initializeCallbacks() {
        if (callbacksInitialized) return;
        ServerLifecycleEvents.SERVER_STARTING.register(FabricWorldGenConfig::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(FabricWorldGenConfig::stop);
        callbacksInitialized = true;
    }

    private static synchronized void start(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("World config must load on the server thread");
        if (SERVERS.containsKey(server)) throw new IllegalStateException("Duplicate world config owner for server");
        Path configRoot = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").toAbsolutePath().normalize();
        if (!CLAIMS.add(configRoot)) throw new IllegalStateException("World config root is already owned: " + configRoot);
        //A fresh manager per server keeps this owner independent of the global config/ manager; the file claim inside the
        //manager resolves to the official per-world path <world>/serverconfig/Mekanism/world.toml.
        FabricMekanismConfigManager manager = new FabricMekanismConfigManager(configRoot);
        try {
            manager.registerAndLoad(MekanismConfig.world);
        } catch (Throwable failure) {
            CLAIMS.remove(configRoot);
            throw failure;
        }
        SERVERS.put(server, new OwnedServer(manager, configRoot));
    }

    private static synchronized void stop(MinecraftServer server) {
        OwnedServer owned = SERVERS.remove(server);
        if (owned == null) return; // a failure before this callback's start is permitted
        try {
            owned.manager().unregister(MekanismConfig.world);
        } finally {
            CLAIMS.remove(owned.configRoot());
        }
    }

    /** Test/debug seam: whether the given server currently owns a loaded world config. */
    public static synchronized boolean isLoaded(MinecraftServer server) {
        return SERVERS.containsKey(server);
    }
}
