package mekanism.fabric.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.common.config.ClientWindowConfigAccess;
import mekanism.common.config.IMekanismConfig;
import mekanism.common.config.MekanismConfigTranslations;
import mekanism.common.config.MekanismConfigType;
import mekanism.common.config.value.CachedValue;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sole manager-loaded client.toml owner: window persistence and the admitted transmitter accessibility setting.
 * Unrelated client keys remain forward-compatible; this is not the full official ClientConfig closure. */
public final class FabricWindowConfig implements IMekanismConfig, ClientWindowConfigAccess.Owner {
    @FunctionalInterface
    interface SaveOperation {
        void save(MekanismConfigSpec spec);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricWindowConfig");
    private static final int SAVE_ATTEMPTS = 4;

    private record WindowValues(MekanismConfigSpec.ConfigValue<Integer> x,
                                MekanismConfigSpec.ConfigValue<Integer> y,
                                @Nullable MekanismConfigSpec.ConfigValue<Boolean> pinned) {
    }

    private final Object admissionMonitor = new Object();
    private final Map<String, WindowValues> windows = new LinkedHashMap<>();
    private final MekanismConfigSpec spec;
    private final MekanismConfigSpec.ConfigValue<Boolean> opaqueTransmitters;
    private final SaveOperation saveOperation;
    private final ExecutorService saveExecutor;
    private final AtomicInteger admittedJobs = new AtomicInteger();
    private final AtomicInteger completedJobs = new AtomicInteger();
    private boolean accepting = true;

    public FabricWindowConfig() {
        this(MekanismConfigSpec::save);
    }

    FabricWindowConfig(SaveOperation saveOperation) {
        this.saveOperation = Objects.requireNonNull(saveOperation, "saveOperation");
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
        MekanismConfigTranslations.CLIENT_ACCESSIBILITY.applyToBuilder(builder).push("accessibility");
        opaqueTransmitters = MekanismConfigTranslations.CLIENT_OPAQUE_TRANSMITTERS.applyToBuilder(builder)
              .gameRestart().define("opaqueTransmitters", false);
        builder.pop();
        MekanismConfigTranslations.CLIENT_LAST_WINDOW_POSITIONS.applyToBuilder(builder).push("window");
        for (WindowType type : WindowType.values()) {
            for (WindowType.ConfigSaveData saveData : type.getSavePaths()) {
                saveData.applyToBuilder(builder).push(saveData.savePath());
                MekanismConfigSpec.ConfigValue<Integer> x = MekanismConfigTranslations.CLIENT_LAST_WINDOW_POSITIONS_X
                      .applyToBuilder(builder).define("x", Integer.MAX_VALUE);
                MekanismConfigSpec.ConfigValue<Integer> y = MekanismConfigTranslations.CLIENT_LAST_WINDOW_POSITIONS_Y
                      .applyToBuilder(builder).define("y", Integer.MAX_VALUE);
                MekanismConfigSpec.ConfigValue<Boolean> pinned = type.canPin()
                      ? MekanismConfigTranslations.CLIENT_LAST_WINDOW_POSITIONS_PINNED.applyToBuilder(builder).define("pinned", false)
                      : null;
                builder.pop();
                windows.put(saveData.savePath(), new WindowValues(x, y, pinned));
            }
        }
        builder.pop();
        spec = builder.build();
        saveExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Mekanism Client Window Config Saver");
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean opaqueTransmitters() {
        return opaqueTransmitters.get();
    }

    @Override
    public ClientWindowConfigAccess.Position get(String saveName) {
        WindowValues values = requireWindow(saveName);
        return new ClientWindowConfigAccess.Position(values.x().get(), values.y().get(),
              values.pinned() != null && values.pinned().get());
    }

    @Override
    public void update(String saveName, int x, int y, boolean pinned, boolean canPin) {
        WindowValues values = requireWindow(saveName);
        if (canPin != (values.pinned() != null)) {
            throw new IllegalArgumentException("Pin capability disagrees with window schema for " + saveName);
        }
        synchronized (admissionMonitor) {
            if (!accepting) {
                throw new IllegalStateException("Client window config is closing");
            }
            boolean changed = false;
            if (values.x().get() != x) {
                values.x().set(x);
                changed = true;
            }
            if (values.y().get() != y) {
                values.y().set(y);
                changed = true;
            }
            if (values.pinned() != null && values.pinned().get() != pinned) {
                values.pinned().set(pinned);
                changed = true;
            }
            if (changed) {
                save();
            }
        }
    }

    @Override
    public void save() {
        synchronized (admissionMonitor) {
            if (!accepting) {
                throw new IllegalStateException("Client window config no longer accepts saves");
            }
            admittedJobs.incrementAndGet();
            saveExecutor.execute(() -> {
                try {
                    for (int attempt = 1; attempt <= SAVE_ATTEMPTS; attempt++) {
                        try {
                            saveOperation.save(spec);
                            return;
                        } catch (RuntimeException failure) {
                            if (attempt == SAVE_ATTEMPTS) {
                                LOGGER.error("Failed to save client window config after {} attempts", SAVE_ATTEMPTS, failure);
                            } else {
                                LOGGER.warn("Failed to save client window config (attempt {}/{}); retrying in the same logical job",
                                      attempt, SAVE_ATTEMPTS, failure);
                            }
                        }
                    }
                } finally {
                    completedJobs.incrementAndGet();
                }
            });
        }
    }

    /** Stops admission and joins all accepted saves. This method never joins while holding the admission monitor. */
    public void stopAndDrain() {
        synchronized (admissionMonitor) {
            if (accepting) {
                accepting = false;
                saveExecutor.shutdown();
            }
        }
        boolean interrupted = false;
        while (true) {
            try {
                if (saveExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                    break;
                }
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    int logicalSaveJobs() {
        return admittedJobs.get();
    }

    int completedSaveJobs() {
        return completedJobs.get();
    }

    @Override
    public String getFileName() {
        return "client";
    }

    @Override
    public String getTranslation() {
        return "configuration.mekanism.client";
    }

    @Override
    public MekanismConfigSpec getConfigSpec() {
        return spec;
    }

    @Override
    public MekanismConfigType getConfigType() {
        return MekanismConfigType.CLIENT;
    }

    @Override
    public void clearCache(boolean unloading) {
        // Values are read directly from the spec, so there is no secondary cache to invalidate.
    }

    @Override
    public void addCachedValue(CachedValue<?> configValue) {
        Objects.requireNonNull(configValue, "configValue");
        throw new UnsupportedOperationException("FabricWindowConfig does not use CachedValue wrappers");
    }

    private WindowValues requireWindow(String saveName) {
        WindowValues values = windows.get(Objects.requireNonNull(saveName, "saveName"));
        if (values == null) {
            throw new IllegalArgumentException("Unknown persisted window " + saveName);
        }
        return values;
    }
}
