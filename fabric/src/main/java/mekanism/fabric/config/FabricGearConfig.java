package mekanism.fabric.config;

import java.nio.file.Path;
import mekanism.common.config.MekanismConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Owns the canonical SERVER gear config for one actual server world at a time. */
public final class FabricGearConfig {
    private static MinecraftServer owner;
    private static FabricMekanismConfigManager manager;
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
    }

    private static synchronized void stop(MinecraftServer server) {
        if (owner != server) return;
        if (!server.isSameThread()) throw new IllegalStateException("Gear config must unload on the server thread");
        try {
            manager.unregister(MekanismConfig.gear);
        } finally {
            owner = null;
            manager = null;
        }
    }

    /** Distinguishes an integrated server's shared instance from a remote client's temporary snapshot. */
    public static synchronized boolean isOwner(MinecraftServer server) {
        return owner == server && owner != null;
    }
}
