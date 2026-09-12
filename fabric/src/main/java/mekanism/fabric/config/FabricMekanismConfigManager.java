package mekanism.fabric.config;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import mekanism.common.config.IMekanismConfig;
import net.fabricmc.loader.api.FabricLoader;

/** Owns Mekanism config paths and load/unload cache invalidation on Fabric. */
public final class FabricMekanismConfigManager {
    public record Registration(Path file, MekanismConfigSpec.LoadResult loadResult) {
    }

    private static final Object CLAIM_MONITOR = new Object();
    private static final Map<Path, IMekanismConfig> PROCESS_FILE_CLAIMS = new HashMap<>();

    private final Path configRoot;
    private final Map<MekanismConfigSpec, IMekanismConfig> knownConfigs = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Path, IMekanismConfig> knownFiles = new HashMap<>();

    public FabricMekanismConfigManager(Path configRoot) {
        this.configRoot = Objects.requireNonNull(configRoot).toAbsolutePath().normalize();
    }

    public static FabricMekanismConfigManager createDefault() {
        return new FabricMekanismConfigManager(FabricLoader.getInstance().getConfigDir());
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
                MekanismConfigSpec.LoadResult result = spec.load(file);
                try {
                    config.clearCache(false);
                } catch (RuntimeException | Error callbackFailure) {
                    spec.unload();
                    throw callbackFailure;
                }
                knownConfigs.put(spec, config);
                knownFiles.put(file, config);
                registered = true;
                return new Registration(file, result);
            } finally {
                if (!registered) {
                    release(file, config);
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
