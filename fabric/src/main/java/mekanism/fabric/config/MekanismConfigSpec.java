package mekanism.fabric.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mekanism-owned immutable config schema backed by a validated TOML document. */
public final class MekanismConfigSpec {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricConfig");

    public enum RestartScope {
        NONE,
        GAME
    }

    public record LoadResult(boolean created, boolean malformed, int correctedValues) {
    }

    private record Metadata(List<String> path, String translation, String comment, RestartScope restartScope) {
        private Metadata {
            path = List.copyOf(path);
        }
    }

    private final Map<String, ConfigValue<?>> values;
    private final List<Metadata> categories;
    private volatile boolean loaded;
    private Path file;
    private CommentedConfig document;

    private MekanismConfigSpec(Map<String, ConfigValue<?>> values, List<Metadata> categories) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.categories = List.copyOf(categories);
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int size() {
        return values.size();
    }

    public Set<String> paths() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values.keySet()));
    }

    public RestartScope restartScope(String dottedPath) {
        return requireValue(dottedPath).restartScope();
    }

    @Nullable
    public String translation(String dottedPath) {
        ConfigValue<?> value = values.get(dottedPath);
        if (value != null) {
            return value.translation();
        }
        for (Metadata category : categories) {
            if (dottedPath.equals(String.join(".", category.path()))) {
                return category.translation();
            }
        }
        throw new IllegalArgumentException("Unknown config path " + dottedPath);
    }

    public boolean isValidValue(String dottedPath, Object candidate) {
        return candidate != null && requireValue(dottedPath).normalize(candidate).isPresent();
    }

    public synchronized LoadResult load(Path target) {
        Objects.requireNonNull(target);
        Path normalizedTarget = target.toAbsolutePath().normalize();
        boolean created = !Files.exists(normalizedTarget);
        boolean malformed = false;
        CommentedConfig candidate;
        if (created) {
            candidate = newDocument();
        } else {
            try (BufferedReader reader = Files.newBufferedReader(normalizedTarget, StandardCharsets.UTF_8)) {
                candidate = new TomlParser().parse(reader);
            } catch (IOException | RuntimeException parseFailure) {
                malformed = true;
                LOGGER.error("Failed to parse config {}; replacing it with validated defaults", normalizedTarget, parseFailure);
                preserveInvalidFile(normalizedTarget);
                candidate = newDocument();
            }
        }

        Map<ConfigValue<?>, Object> resolved = new IdentityHashMap<>();
        int corrected = 0;
        for (ConfigValue<?> value : values.values()) {
            Object raw = candidate.contains(value.path()) ? candidate.getRaw(value.path()) : null;
            Optional<?> normalized = raw == null ? Optional.empty() : value.normalize(raw);
            Object accepted;
            if (normalized.isPresent()) {
                accepted = normalized.get();
            } else {
                accepted = value.getDefault();
                corrected++;
                if (raw != null) {
                    LOGGER.warn("Rejected config value {}={} and restored default {}", value.dottedPath(), raw, accepted);
                }
            }
            resolved.put(value, accepted);
            candidate.set(value.path(), accepted);
        }
        applyComments(candidate);
        if (created || malformed || corrected > 0) {
            writeDocument(normalizedTarget, candidate);
        }

        // Publish only after the entire document has parsed, validated, and any correction was persisted.
        file = normalizedTarget;
        document = candidate;
        for (Map.Entry<ConfigValue<?>, Object> entry : resolved.entrySet()) {
            entry.getKey().publishLoaded(entry.getValue());
        }
        loaded = true;
        return new LoadResult(created, malformed, corrected);
    }

    public synchronized void save() {
        if (!loaded || file == null || document == null) {
            throw new IllegalStateException("Config spec has not been loaded");
        }
        for (ConfigValue<?> value : values.values()) {
            document.set(value.path(), value.get());
        }
        applyComments(document);
        saveDocument();
    }

    public synchronized void unload() {
        loaded = false;
        file = null;
        document = null;
    }

    private ConfigValue<?> requireValue(String dottedPath) {
        ConfigValue<?> value = values.get(dottedPath);
        if (value == null) {
            throw new IllegalArgumentException("Unknown config path " + dottedPath);
        }
        return value;
    }

    private synchronized <T> void update(ConfigValue<T> value, T updated) {
        value.publishLoaded(updated);
        if (loaded && document != null) {
            document.set(value.path(), updated);
        }
    }

    private void applyComments(CommentedConfig target) {
        for (Metadata category : categories) {
            if (category.comment() != null) {
                target.setComment(category.path(), category.comment());
            }
        }
        for (ConfigValue<?> value : values.values()) {
            if (value.comment() != null) {
                target.setComment(value.path(), value.comment());
            }
        }
    }

    private void saveDocument() {
        writeDocument(Objects.requireNonNull(file), Objects.requireNonNull(document));
    }

    private static void writeDocument(Path target, CommentedConfig document) {
        Path parent = Objects.requireNonNull(target.getParent(), "config file requires a parent directory");
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                new TomlWriter().write(document, writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException writeFailure) {
            throw new UncheckedIOException("Failed to save config " + target, writeFailure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    LOGGER.warn("Failed to remove temporary config file {}", temporary, cleanupFailure);
                }
            }
        }
    }

    private static CommentedConfig newDocument() {
        return TomlFormat.newConfig(LinkedHashMap::new);
    }

    private static void preserveInvalidFile(Path source) {
        try {
            Files.copy(source, source.resolveSibling(source.getFileName() + ".invalid"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException backupFailure) {
            throw new UncheckedIOException("Failed to preserve malformed config " + source, backupFailure);
        }
    }

    public static final class ConfigValue<T> {
        private MekanismConfigSpec owner;
        private final List<String> path;
        private final String dottedPath;
        private final T defaultValue;
        private final Function<Object, Optional<T>> normalizer;
        private final String translation;
        private final String comment;
        private final RestartScope restartScope;
        private volatile T currentValue;

        private ConfigValue(List<String> path, T defaultValue, Function<Object, Optional<T>> normalizer,
              String translation, String comment, RestartScope restartScope) {
            this.path = List.copyOf(path);
            dottedPath = String.join(".", path);
            this.defaultValue = defaultValue;
            this.normalizer = normalizer;
            this.translation = translation;
            this.comment = comment;
            this.restartScope = restartScope;
            currentValue = defaultValue;
        }

        public T getDefault() {
            return defaultValue;
        }

        public T get() {
            MekanismConfigSpec spec = requireOwner();
            synchronized (spec) {
                return currentValue;
            }
        }

        public void set(T value) {
            T normalized = normalize(value).orElseThrow(() -> new IllegalArgumentException("Invalid value for " + dottedPath + ": " + value));
            requireOwner().update(this, normalized);
        }

        private void attach(MekanismConfigSpec owner) {
            if (this.owner != null) {
                throw new IllegalStateException("Config value " + dottedPath + " is already attached");
            }
            this.owner = Objects.requireNonNull(owner);
        }

        private MekanismConfigSpec requireOwner() {
            if (owner == null) {
                throw new IllegalStateException("Config value " + dottedPath + " was accessed before the spec was built");
            }
            return owner;
        }

        private List<String> path() {
            return path;
        }

        private String dottedPath() {
            return dottedPath;
        }

        private Optional<T> normalize(Object raw) {
            return normalizer.apply(raw);
        }

        @SuppressWarnings("unchecked")
        private void publishLoaded(Object value) {
            currentValue = (T) value;
        }

        private String translation() {
            return translation;
        }

        private String comment() {
            return comment;
        }

        private RestartScope restartScope() {
            return restartScope;
        }
    }

    public static final class Builder {
        private final Deque<String> path = new ArrayDeque<>();
        private final Map<String, ConfigValue<?>> definitions = new LinkedHashMap<>();
        private final Map<String, Metadata> categories = new LinkedHashMap<>();
        private String pendingTranslation;
        private String pendingComment;
        private RestartScope pendingRestart = RestartScope.NONE;
        private boolean built;

        public Builder translation(String translation) {
            ensureMutable();
            pendingTranslation = Objects.requireNonNull(translation);
            return this;
        }

        public Builder comment(String comment) {
            ensureMutable();
            pendingComment = Objects.requireNonNull(comment);
            return this;
        }

        public Builder gameRestart() {
            ensureMutable();
            pendingRestart = RestartScope.GAME;
            return this;
        }

        public Builder push(String category) {
            ensureMutable();
            validateSegment(category);
            List<String> categoryPath = new ArrayList<>(path);
            categoryPath.add(category);
            String dottedPath = String.join(".", categoryPath);
            if (categories.containsKey(dottedPath)) {
                throw new IllegalArgumentException("Duplicate config category " + dottedPath);
            }
            for (String valuePath : definitions.keySet()) {
                if (dottedPath.equals(valuePath) || dottedPath.startsWith(valuePath + ".")) {
                    throw new IllegalArgumentException("Config category " + dottedPath + " collides with value " + valuePath);
                }
            }
            categories.put(dottedPath, new Metadata(categoryPath, pendingTranslation, pendingComment, pendingRestart));
            path.addLast(category);
            clearPending();
            return this;
        }

        public Builder pop() {
            ensureMutable();
            if (path.isEmpty()) {
                throw new IllegalStateException("Cannot pop the root config category");
            }
            if (hasPendingMetadata()) {
                throw new IllegalStateException("Config metadata was not attached to a category or value");
            }
            path.removeLast();
            return this;
        }

        public ConfigValue<Integer> defineInRange(String name, int defaultValue, int minimum, int maximum) {
            if (minimum > maximum || defaultValue < minimum || defaultValue > maximum) {
                throw new IllegalArgumentException("Invalid integer range/default for " + name);
            }
            return define(name, defaultValue, raw -> normalizeInteger(raw, minimum, maximum));
        }

        public ConfigValue<Long> defineInRange(String name, long defaultValue, long minimum, long maximum) {
            if (minimum > maximum || defaultValue < minimum || defaultValue > maximum) {
                throw new IllegalArgumentException("Invalid long range/default for " + name);
            }
            return define(name, defaultValue, raw -> normalizeLong(raw, minimum, maximum));
        }

        public ConfigValue<Double> defineInRange(String name, double defaultValue, double minimum, double maximum) {
            if (!Double.isFinite(defaultValue) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                  || minimum > maximum || defaultValue < minimum || defaultValue > maximum) {
                throw new IllegalArgumentException("Invalid floating-point range/default for " + name);
            }
            return define(name, defaultValue, raw -> normalizeDouble(raw, minimum, maximum));
        }

        public MekanismConfigSpec build() {
            ensureMutable();
            if (!path.isEmpty()) {
                throw new IllegalStateException("Unclosed config category " + String.join(".", path));
            }
            if (hasPendingMetadata()) {
                throw new IllegalStateException("Config metadata was not attached to a category or value");
            }
            built = true;
            MekanismConfigSpec spec = new MekanismConfigSpec(definitions, new ArrayList<>(categories.values()));
            definitions.values().forEach(value -> value.attach(spec));
            return spec;
        }

        private <T> ConfigValue<T> define(String name, T defaultValue, Function<Object, Optional<T>> normalizer) {
            ensureMutable();
            validateSegment(name);
            List<String> valuePath = new ArrayList<>(path);
            valuePath.add(name);
            String dottedPath = String.join(".", valuePath);
            for (String existingPath : definitions.keySet()) {
                if (dottedPath.startsWith(existingPath + ".") || existingPath.startsWith(dottedPath + ".")) {
                    throw new IllegalArgumentException("Config value " + dottedPath + " collides with value " + existingPath);
                }
            }
            for (String categoryPath : categories.keySet()) {
                if (dottedPath.equals(categoryPath) || categoryPath.startsWith(dottedPath + ".")) {
                    throw new IllegalArgumentException("Config value " + dottedPath + " collides with category " + categoryPath);
                }
            }
            ConfigValue<T> value = new ConfigValue<>(valuePath, defaultValue, normalizer,
                  pendingTranslation, pendingComment, pendingRestart);
            if (definitions.putIfAbsent(dottedPath, value) != null) {
                throw new IllegalArgumentException("Duplicate config value " + dottedPath);
            }
            clearPending();
            return value;
        }

        private void clearPending() {
            pendingTranslation = null;
            pendingComment = null;
            pendingRestart = RestartScope.NONE;
        }

        private boolean hasPendingMetadata() {
            return pendingTranslation != null || pendingComment != null || pendingRestart != RestartScope.NONE;
        }

        private void ensureMutable() {
            if (built) {
                throw new IllegalStateException("Config builder has already built its spec");
            }
        }

        private static void validateSegment(String segment) {
            if (segment == null || !segment.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("Invalid config path segment " + segment);
            }
        }

        private static Optional<Integer> normalizeInteger(Object raw, int minimum, int maximum) {
            if (!(raw instanceof Number number)) {
                return Optional.empty();
            }
            double asDouble = number.doubleValue();
            long asLong = number.longValue();
            if (!Double.isFinite(asDouble) || asDouble != asLong || asLong < minimum || asLong > maximum) {
                return Optional.empty();
            }
            return Optional.of((int) asLong);
        }

        private static Optional<Long> normalizeLong(Object raw, long minimum, long maximum) {
            final long value;
            try {
                if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
                    value = ((Number) raw).longValue();
                } else if (raw instanceof BigInteger integer) {
                    value = integer.longValueExact();
                } else if (raw instanceof BigDecimal decimal) {
                    value = decimal.longValueExact();
                } else if (raw instanceof Float floating) {
                    if (!Float.isFinite(floating)) {
                        return Optional.empty();
                    }
                    value = new BigDecimal(Float.toString(floating)).longValueExact();
                } else if (raw instanceof Double floating) {
                    if (!Double.isFinite(floating)) {
                        return Optional.empty();
                    }
                    value = BigDecimal.valueOf(floating).longValueExact();
                } else {
                    return Optional.empty();
                }
            } catch (ArithmeticException ignored) {
                return Optional.empty();
            }
            return value >= minimum && value <= maximum ? Optional.of(value) : Optional.empty();
        }

        private static Optional<Double> normalizeDouble(Object raw, double minimum, double maximum) {
            if (!(raw instanceof Number number)) {
                return Optional.empty();
            }
            double value = number.doubleValue();
            return Double.isFinite(value) && value >= minimum && value <= maximum ? Optional.of(value) : Optional.empty();
        }
    }
}
