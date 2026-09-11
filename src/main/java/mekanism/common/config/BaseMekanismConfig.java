package mekanism.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mekanism.common.config.value.CachedValue;
import mekanism.fabric.config.MekanismConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseMekanismConfig implements IMekanismConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/Config");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Mekanism Config Saver");
        thread.setDaemon(true);
        return thread;
    });

    private final List<CachedValue<?>> cachedConfigValues = new ArrayList<>();

    @Override
    public void clearCache(boolean unloading) {
        for (CachedValue<?> cachedConfigValue : cachedConfigValues) {
            cachedConfigValue.clearCache(unloading);
        }
    }

    @Override
    public void addCachedValue(CachedValue<?> configValue) {
        cachedConfigValues.add(configValue);
    }

    @Override
    public void save() {
        EXECUTOR.submit(new ConfigSaver(getConfigSpec()));
    }

    private static class ConfigSaver implements Runnable {
        private final MekanismConfigSpec configSpec;
        private int retries;

        private ConfigSaver(MekanismConfigSpec configSpec) {
            this.configSpec = configSpec;
        }

        @Override
        public void run() {
            try {
                configSpec.save();
            } catch (Exception e) {
                LOGGER.error("Failed to save config", e);
                if (retries++ < 3) {
                    EXECUTOR.submit(this);
                } else {
                    LOGGER.error("Giving up");
                }
            }
        }
    }
}
