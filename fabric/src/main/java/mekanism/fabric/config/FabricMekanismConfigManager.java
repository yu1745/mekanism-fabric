package mekanism.fabric.config;

import com.electronwill.nightconfig.core.file.FileWatcher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import mekanism.common.config.IMekanismConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns Mekanism config paths and load/unload cache invalidation on Fabric. */
public final class FabricMekanismConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricConfig");
    public record Registration(Path file, MekanismConfigSpec.LoadResult loadResult) {
    }

    private static final Object CLAIM_MONITOR = new Object();
    private static final Map<Path, IMekanismConfig> PROCESS_FILE_CLAIMS = new HashMap<>();

    private final Path configRoot;
    private final Path overrideRoot;
    private final Path defaultConfigRoot;
    private final Map<MekanismConfigSpec, IMekanismConfig> knownConfigs = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Path, IMekanismConfig> knownFiles = new HashMap<>();
    private Object watchOwner;
    private Executor reloadExecutor;

    public FabricMekanismConfigManager(Path configRoot) {
        this(configRoot, null, null);
    }

    private FabricMekanismConfigManager(Path configRoot, Path overrideRoot, Path defaultConfigRoot) {
        this.configRoot = Objects.requireNonNull(configRoot).toAbsolutePath().normalize();
        this.overrideRoot = overrideRoot == null ? null : overrideRoot.toAbsolutePath().normalize();
        this.defaultConfigRoot = defaultConfigRoot == null ? null : defaultConfigRoot.toAbsolutePath().normalize();
    }

    public static FabricMekanismConfigManager createDefault() {
        FabricLoader loader = FabricLoader.getInstance();
        return new FabricMekanismConfigManager(loader.getConfigDir(), null, loader.getGameDir().resolve("defaultconfigs"));
    }

    /** NeoForge 21.1 uses global SERVER files by default, with an existing world override selected per filename. */
    public static FabricMekanismConfigManager createForServer(Path worldOverrides) {
        FabricLoader loader = FabricLoader.getInstance();
        return new FabricMekanismConfigManager(loader.getConfigDir(), worldOverrides, loader.getGameDir().resolve("defaultconfigs"));
    }

    public Registration registerAndLoad(IMekanismConfig config) {
        Objects.requireNonNull(config);
        String fileName = config.getFileName();
        if (fileName == null || !fileName.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid Mekanism config filename " + fileName);
        }
        MekanismConfigSpec spec = config.getConfigSpec();
        synchronized (knownConfigs) {
            if (knownConfigs.containsKey(spec)) {
                throw new IllegalStateException("Config spec is already registered: " + fileName);
            }
            Path mekanismDirectory = configRoot.resolve("Mekanism").normalize();
            if (overrideRoot != null && Files.exists(overrideRoot.resolve("Mekanism").resolve(fileName + ".toml"))) {
                mekanismDirectory = overrideRoot.resolve("Mekanism").normalize();
            }
            Path file = mekanismDirectory.resolve(fileName + ".toml").normalize();
            if (!file.startsWith(mekanismDirectory)) {
                throw new IllegalArgumentException("Config filename escapes Mekanism config directory: " + fileName);
            }
            if (knownFiles.containsKey(file)) {
                throw new IllegalStateException("Config file is already registered: " + file);
            }
            claim(file, config);
            boolean registered = false;
            try {
                if (!Files.exists(file) && defaultConfigRoot != null) {
                    Path template = defaultConfigRoot.resolve("Mekanism").resolve(fileName + ".toml");
                    if (Files.isRegularFile(template)) {
                        try {
                            Files.createDirectories(file.getParent());
                            Files.copy(template, file);
                        } catch (IOException failure) {
                            throw new UncheckedIOException("Unable to initialize config from " + template, failure);
                        }
                    }
                }
                MekanismConfigSpec.LoadResult result = spec.loadForOwner(file);
                try {
                    config.clearCache(false);
                } catch (RuntimeException | Error callbackFailure) {
                    spec.unload();
                    throw callbackFailure;
                }
                knownConfigs.put(spec, config);
                knownFiles.put(file, config);
                if (watchOwner != null) watch(file, config, watchOwner);
                registered = true;
                return new Registration(file, result);
            } finally {
                if (!registered) {
                    knownConfigs.remove(spec);
                    knownFiles.remove(file);
                    spec.unload();
                    try {
                        if (watchOwner != null) FileWatcher.defaultInstance().removeWatch(file);
                    } finally {
                        release(file, config);
                    }
                }
            }
        }
    }

    public void unregister(IMekanismConfig config) {
        Objects.requireNonNull(config);
        synchronized (knownConfigs) {
            MekanismConfigSpec spec = config.getConfigSpec();
            if (knownConfigs.get(spec) != config) {
                throw new IllegalStateException("Config is not registered: " + config.getFileName());
            }
            Path file = findFile(config);
            if (watchOwner != null) FileWatcher.defaultInstance().removeWatch(file);
            RuntimeException callbackFailure = null;
            try {
                config.clearCache(true);
            } catch (RuntimeException failure) {
                callbackFailure = failure;
            } finally {
                spec.unload();
                knownConfigs.remove(spec);
                knownFiles.remove(file);
                release(file, config);
            }
            if (callbackFailure != null) {
                throw callbackFailure;
            }
        }
    }

    public void unloadAll() {
        stopWatching();
        RuntimeException firstFailure = null;
        synchronized (knownConfigs) {
            for (Map.Entry<MekanismConfigSpec, IMekanismConfig> entry : knownConfigs.entrySet()) {
                IMekanismConfig config = entry.getValue();
                Path file = findFile(config);
                try {
                    config.clearCache(true);
                } catch (RuntimeException failure) {
                    if (firstFailure == null) {
                        firstFailure = failure;
                    } else {
                        firstFailure.addSuppressed(failure);
                    }
                } finally {
                    entry.getKey().unload();
                    release(file, config);
                }
            }
            knownConfigs.clear();
            knownFiles.clear();
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    /** The real physical client or server lifetime supplies its executor; file-watcher threads never run domain callbacks. */
    public void beginWatching(Executor executor) {
        synchronized (knownConfigs) {
            if (watchOwner != null) throw new IllegalStateException("Config watcher already has an owner");
            reloadExecutor = Objects.requireNonNull(executor);
            Object lifetime = watchOwner = new Object();
            try {
                knownFiles.forEach((file, config) -> watch(file, config, lifetime));
            } catch (RuntimeException failure) {
                stopWatching();
                throw failure;
            }
        }
    }

    private void watch(Path file, IMekanismConfig config, Object lifetime) {
        Executor executor = reloadExecutor;
        Runnable queue = () -> {
            synchronized (knownConfigs) {
                if (watchOwner != lifetime || knownFiles.get(file) != config) return;
            }
            executor.execute(() -> reload(file, config, lifetime));
        };
        FileWatcher.defaultInstance().addWatch(file, queue);
        // Pick up edits made while no world/client executor owned this process config.
        queue.run();
    }

    private void reload(Path file, IMekanismConfig config, Object lifetime) {
        synchronized (knownConfigs) {
            if (watchOwner != lifetime || knownFiles.get(file) != config) return;
            try {
                MekanismConfigSpec.LoadResult result = config.getConfigSpec().reload(file);
                config.clearCache(false);
                LOGGER.info("MEKANISM_FABRIC_CONFIG_RELOAD file={} corrected={} thread={}", file, result.correctedValues(),
                      Thread.currentThread().getName());
            } catch (RuntimeException failure) {
                LOGGER.error("Unable to reload Mekanism config {}", file, failure);
            }
        }
    }

    public void stopWatching() {
        synchronized (knownConfigs) {
            if (watchOwner == null) return;
            // Invalidate already queued work before unregistering the asynchronous watcher.
            watchOwner = null;
            knownFiles.keySet().forEach(FileWatcher.defaultInstance()::removeWatch);
            reloadExecutor = null;
        }
    }

    private Path findFile(IMekanismConfig config) {
        return knownFiles.entrySet().stream().filter(entry -> entry.getValue() == config).map(Map.Entry::getKey).findFirst()
              .orElseThrow(() -> new IllegalStateException("Missing file claim for config " + config.getFileName()));
    }

    private static void claim(Path file, IMekanismConfig config) {
        synchronized (CLAIM_MONITOR) {
            IMekanismConfig existing = PROCESS_FILE_CLAIMS.putIfAbsent(file, config);
            if (existing != null) {
                throw new IllegalStateException("Config file is already owned process-wide: " + file);
            }
        }
    }

    private static void release(Path file, IMekanismConfig config) {
        synchronized (CLAIM_MONITOR) {
            if (!PROCESS_FILE_CLAIMS.remove(file, config)) {
                throw new IllegalStateException("Config file claim is not owned by the requested config: " + file);
            }
        }
    }
}
