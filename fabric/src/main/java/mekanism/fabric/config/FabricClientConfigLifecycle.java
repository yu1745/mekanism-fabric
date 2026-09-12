package mekanism.fabric.config;

import java.nio.file.Path;
import mekanism.common.config.ClientWindowConfigAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns the window config for exactly one physical Fabric client lifetime. */
public final class FabricClientConfigLifecycle implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricClientConfig");
    private static FabricClientConfigLifecycle active;

    private final FabricMekanismConfigManager manager;
    private final FabricWindowConfig windowConfig;
    private boolean closed;

    private FabricClientConfigLifecycle(FabricMekanismConfigManager manager) {
        this.manager = manager;
        windowConfig = new FabricWindowConfig();
        manager.registerAndLoad(windowConfig);
        boolean installed = false;
        try {
            ClientWindowConfigAccess.install(windowConfig, Thread.currentThread());
            installed = true;
        } finally {
            if (!installed) {
                windowConfig.stopAndDrain();
                manager.unregister(windowConfig);
            }
        }
    }

    /** Starts the process singleton and hooks only physical-client shutdown, not connection lifecycle events. */
    public static synchronized FabricClientConfigLifecycle initialize() {
        if (active != null) {
            throw new IllegalStateException("Fabric client config lifecycle is already initialized");
        }
        FabricClientConfigLifecycle lifecycle = new FabricClientConfigLifecycle(FabricMekanismConfigManager.createDefault());
        active = lifecycle;
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
        LOGGER.info("MEKANISM_FABRIC_CLIENT_CONFIG_INITIALIZED owner={} thread={}",
              System.identityHashCode(lifecycle.windowConfig), Thread.currentThread().getName());
        return lifecycle;
    }

    /** Creates an isolated lifetime for tests and smoke fixtures without touching the process singleton. */
    static FabricClientConfigLifecycle create(Path configRoot) {
        return new FabricClientConfigLifecycle(new FabricMekanismConfigManager(configRoot));
    }

    public static synchronized void shutdown() {
        FabricClientConfigLifecycle lifecycle = active;
        LOGGER.info("MEKANISM_FABRIC_CLIENT_CONFIG_SHUTDOWN active={} thread={}", lifecycle != null, Thread.currentThread().getName());
        if (lifecycle != null) {
            // Clear publication only after a successful owner-thread close. A wrong-thread caller must not orphan the owner.
            lifecycle.close();
            active = null;
        }
    }

    FabricWindowConfig windowConfig() {
        return windowConfig;
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            ClientWindowConfigAccess.verifyOwnerThread(windowConfig);
            closed = true;
        }
        windowConfig.stopAndDrain();
        RuntimeException unloadFailure = null;
        try {
            manager.unregister(windowConfig);
        } catch (RuntimeException failure) {
            unloadFailure = failure;
        } finally {
            ClientWindowConfigAccess.remove(windowConfig);
        }
        if (unloadFailure != null) {
            throw unloadFailure;
        }
    }
}
