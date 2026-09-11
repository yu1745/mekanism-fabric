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
            MekanismConfigSpec.LoadResult result = spec.load(file);
            try {
                config.clearCache(false);
            } catch (RuntimeException | Error callbackFailure) {
                spec.unload();
                throw callbackFailure;
            }
            knownConfigs.put(spec, config);
            knownFiles.put(file, config);
            return new Registration(file, result);
        }
    }

    public void unloadAll() {
        synchronized (knownConfigs) {
            for (Map.Entry<MekanismConfigSpec, IMekanismConfig> entry : knownConfigs.entrySet()) {
                entry.getValue().clearCache(true);
                entry.getKey().unload();
            }
            knownConfigs.clear();
            knownFiles.clear();
        }
    }
}
