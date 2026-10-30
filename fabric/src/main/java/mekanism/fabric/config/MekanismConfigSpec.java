package mekanism.fabric.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
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
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mekanism-owned immutable config schema backed by a validated TOML document. */
public final class MekanismConfigSpec {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricConfig");

    public enum RestartScope {
        NONE,
        GAME,
        WORLD
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
            try {
                String source = Files.readString(normalizedTarget, StandardCharsets.UTF_8);
                validateTomlIntegerRange(source);
                try (BufferedReader reader = new BufferedReader(new java.io.StringReader(source))) {
                    candidate = new TomlParser().parse(reader);
                }
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
            ensureParentTables(candidate, value.path());
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
            candidate.set(value.path(), value.encodeUntyped(accepted));
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

    /** A strict scalar snapshot for the SERVER gear configuration; no document or unknown keys leave the owner. */
    public synchronized Map<String, Object> scalarSnapshot() {
        if (!loaded) throw new IllegalStateException("Config spec is not loaded");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigValue<?>> entry : values.entrySet()) {
            Object value = entry.getValue().currentValue;
            if (!(value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Double)) {
                throw new IllegalStateException("Non-scalar config value at " + entry.getKey());
            }
            snapshot.put(entry.getKey(), value);
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /** Validate the complete snapshot before changing any value. Does not read or write a TOML file. */
    public synchronized void installRemoteScalars(Map<String, Object> snapshot) {
        Objects.requireNonNull(snapshot);
        if (loaded || file != null || document != null) throw new IllegalStateException("Config spec already has an owner");
        if (!snapshot.keySet().equals(values.keySet())) throw new IllegalArgumentException("Config snapshot schema mismatch");
        Map<ConfigValue<?>, Object> resolved = new IdentityHashMap<>();
        for (Map.Entry<String, ConfigValue<?>> entry : values.entrySet()) {
            ConfigValue<?> value = entry.getValue();
            Object candidate = snapshot.get(entry.getKey());
            Object defaultValue = value.getDefault();
            if (candidate == null || !(defaultValue instanceof Boolean || defaultValue instanceof Integer
                  || defaultValue instanceof Long || defaultValue instanceof Double) || candidate.getClass() != defaultValue.getClass()) {
                throw new IllegalArgumentException("Invalid config snapshot type for " + entry.getKey());
            }
            Object normalized = value.normalize(candidate).orElseThrow(() -> new IllegalArgumentException("Invalid config snapshot value for " + entry.getKey()));
            resolved.put(value, normalized);
        }
        for (Map.Entry<ConfigValue<?>, Object> entry : resolved.entrySet()) entry.getKey().publishLoaded(entry.getValue());
        loaded = true;
    }

    /** Reset a remote-only snapshot while still loaded, so callers can invalidate listeners before unloading it. */
    public synchronized void resetRemoteScalars() {
        if (!loaded || file != null || document != null) throw new IllegalStateException("Config spec is not remote-owned");
        for (ConfigValue<?> value : values.values()) value.publishLoaded(value.getDefault());
    }

    public synchronized void save() {
        if (!loaded || file == null || document == null) {
            throw new IllegalStateException("Config spec has not been loaded");
        }
        for (ConfigValue<?> value : values.values()) {
            document.set(value.path(), value.encodeCurrent());
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
            document.set(value.path(), value.encode(updated));
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

    /** Replaces scalar parents of owned values while retaining every unrelated table and key. */
    private static void ensureParentTables(CommentedConfig target, List<String> valuePath) {
        for (int length = 1; length < valuePath.size(); length++) {
            List<String> parentPath = valuePath.subList(0, length);
            Object parent = target.getRaw(parentPath);
            if (parent != null && !(parent instanceof Config)) {
                target.remove(parentPath);
                target.set(parentPath, newDocument());
            }
        }
    }

    /** Rejects valid-looking TOML integer value tokens that Night Config would wrap before schema validation. */
    private static void validateTomlIntegerRange(String source) {
        char previous = '\0';
        boolean lineStart = true;
        boolean tableHeader = false;
        for (int index = 0; index < source.length();) {
            char current = source.charAt(index);
            if (Character.isWhitespace(current)) {
                if (current == '\n' || current == '\r') {
                    lineStart = true;
                    tableHeader = false;
                }
                index++;
                continue;
            }
            if (current == '#') {
                index = skipToLineEnd(source, index + 1);
                continue;
            }
            if (current == '"' || current == '\'') {
                index = skipTomlString(source, index, current);
                previous = 'v';
                lineStart = false;
                continue;
            }
            if ("=[]{},".indexOf(current) >= 0) {
                if (current == '[' && lineStart && previous != '=' && previous != '[' && previous != ',') {
                    tableHeader = true;
                }
                previous = current;
                lineStart = false;
                index++;
                continue;
            }
            int start = index;
            lineStart = false;
            while (index < source.length()) {
                char character = source.charAt(index);
                if (Character.isWhitespace(character) || character == '#' || "=[]{},\"'".indexOf(character) >= 0) {
                    break;
                }
                index++;
            }
            String token = source.substring(start, index);
            int next = index;
            while (next < source.length() && Character.isWhitespace(source.charAt(next))) {
                next++;
            }
            boolean keyToken = next < source.length() && (source.charAt(next) == '=' || source.charAt(next) == '.');
            if (!tableHeader && !keyToken && (previous == '=' || previous == '[' || previous == ',') && isTomlInteger(token)) {
                BigInteger value = parseTomlInteger(token);
                if (value.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0
                      || value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                    throw new IllegalArgumentException("TOML integer outside signed 64-bit range: " + token);
                }
            }
            previous = 'v';
        }
    }

    private static int skipToLineEnd(String source, int index) {
        while (index < source.length() && source.charAt(index) != '\n') {
            index++;
        }
        return index;
    }

    private static int skipTomlString(String source, int start, char quote) {
        boolean multiline = start + 2 < source.length() && source.charAt(start + 1) == quote && source.charAt(start + 2) == quote;
        int index = start + (multiline ? 3 : 1);
        while (index < source.length()) {
            if (multiline && index + 2 < source.length() && source.charAt(index) == quote
                  && source.charAt(index + 1) == quote && source.charAt(index + 2) == quote) {
                int end = index + 3;
                for (int extra = 0; extra < 2 && end < source.length() && source.charAt(end) == quote; extra++) {
                    end++;
                }
                return end;
            }
            char current = source.charAt(index++);
            if (!multiline && current == quote) {
                return index;
            } else if (!multiline && (current == '\n' || current == '\r')) {
                throw new IllegalArgumentException("Unterminated single-line TOML string");
            }
            if (quote == '"' && current == '\\' && index < source.length()) {
                index++;
            }
        }
        return index;
    }

    private static boolean isTomlInteger(String token) {
        return token.matches("[+-]?(?:0|[1-9](?:_?[0-9])*)")
              || token.matches("0x[0-9A-Fa-f](?:_?[0-9A-Fa-f])*")
              || token.matches("0o[0-7](?:_?[0-7])*")
              || token.matches("0b[01](?:_?[01])*");
    }

    private static BigInteger parseTomlInteger(String token) {
        String compact = token.replace("_", "");
        if (compact.startsWith("0x") || compact.startsWith("0o") || compact.startsWith("0b")) {
            int radix = compact.charAt(1) == 'x' ? 16 : compact.charAt(1) == 'o' ? 8 : 2;
            return new BigInteger(compact.substring(2), radix);
        }
        return new BigInteger(compact);
    }

    private static void preserveInvalidFile(Path source) {
        try {
            Files.copy(source, source.resolveSibling(source.getFileName() + ".invalid"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException backupFailure) {
            throw new UncheckedIOException("Failed to preserve malformed config " + source, backupFailure);
        }
    }

    public static class ConfigValue<T> {
        private MekanismConfigSpec owner;
        private final List<String> path;
        private final String dottedPath;
        private final T defaultValue;
        private final Function<Object, Optional<T>> normalizer;
        private final Function<T, Object> encoder;
        private final String translation;
        private final String comment;
        private final RestartScope restartScope;
        private volatile T currentValue;

        protected ConfigValue(List<String> path, T defaultValue, Function<Object, Optional<T>> normalizer,
              Function<T, Object> encoder, String translation, String comment, RestartScope restartScope) {
            this.path = List.copyOf(path);
            dottedPath = String.join(".", path);
            this.defaultValue = defaultValue;
            this.normalizer = normalizer;
            this.encoder = encoder;
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

        private Object encode(T value) {
            return encoder.apply(value);
        }

        private Object encodeCurrent() {
            return encode(currentValue);
        }

        @SuppressWarnings("unchecked")
        private Object encodeUntyped(Object value) {
            return encode((T) value);
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

    /** Distinct backing type preserving the official cached-enum public overload. */
    public static final class EnumValue<T extends Enum<T>> extends ConfigValue<T> {
        private EnumValue(List<String> path, T defaultValue, Function<Object, Optional<T>> normalizer,
              Function<T, Object> encoder, String translation, String comment, RestartScope restartScope) {
            super(path, defaultValue, normalizer, encoder, translation, comment, restartScope);
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

        /**
         * Multi-line comment, mirroring NeoForge's {@code ModConfigSpec.Builder#comment(String...)} which joins the lines with newlines.
         */
        public Builder comment(String... comments) {
            ensureMutable();
            if (comments.length == 0) {
                throw new IllegalArgumentException("At least one comment line is required");
            }
            for (String line : comments) {
                Objects.requireNonNull(line, "comment line");
            }
            pendingComment = String.join("\n", comments);
            return this;
        }

        public Builder gameRestart() {
            ensureMutable();
            pendingRestart = RestartScope.GAME;
            return this;
        }

        public Builder worldRestart() {
            ensureMutable();
            pendingRestart = RestartScope.WORLD;
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

        /**
         * Pops the given number of categories, mirroring NeoForge's {@code ModConfigSpec.Builder#pop(int)}.
         */
        public Builder pop(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("At least one category must be popped");
            }
            for (int i = 0; i < count; i++) {
                pop();
            }
            return this;
        }

        public ConfigValue<Boolean> define(String name, boolean defaultValue) {
            return define(name, defaultValue, Builder::normalizeBoolean);
        }

        /** Defines a strict enum encoded by its declared Java name. */
        public <T extends Enum<T>> EnumValue<T> defineEnum(String name, T defaultValue) {
            Objects.requireNonNull(defaultValue, "defaultValue");
            Class<T> type = defaultValue.getDeclaringClass();
            Function<Object, Optional<T>> normalizer = raw -> {
                if (type.isInstance(raw)) {
                    return Optional.of(type.cast(raw));
                }
                if (raw instanceof String enumName) {
                    try {
                        return Optional.of(Enum.valueOf(type, enumName));
                    } catch (IllegalArgumentException ignored) {
                        return Optional.empty();
                    }
                }
                return Optional.empty();
            };
            return defineEnumValue(name, defaultValue, normalizer, Enum::name);
        }

        /** Defines a strict signed-32-bit integer. Floating-point and decimal values are rejected even when integral. */
        public ConfigValue<Integer> define(String name, int defaultValue) {
            return define(name, defaultValue, Builder::normalizeStrictInteger);
        }

        /**
         * Integer definition with a raw-value validator, mirroring NeoForge's {@code define(String, T, Predicate)}. The raw stored value must be an integer
         * accepted by the validator. Named distinctly from {@code define} so lambda call sites stay unambiguous against the Function-based overload.
         */
        public ConfigValue<Integer> defineValidated(String name, int defaultValue, Predicate<Object> validator) {
            Objects.requireNonNull(validator, "validator");
            return define(name, defaultValue, raw -> raw instanceof Integer value && validator.test(raw)
                  ? Optional.of(value) : Optional.empty());
        }

        /**
         * Defines the immutable list schema needed by admitted common config values. The new-element supplier is
         * null-checked for source compatibility, but is intentionally not invoked because this spec has no UI/editor.
         */
        public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(String name,
              Supplier<List<? extends T>> defaultSupplier, Supplier<T> newElementSupplier, Predicate<Object> elementValidator) {
            ensureMutable();
            Objects.requireNonNull(defaultSupplier, "defaultSupplier");
            Objects.requireNonNull(newElementSupplier, "newElementSupplier");
            Objects.requireNonNull(elementValidator, "elementValidator");
            List<? extends T> defaultValue = Builder.<T>normalizeList(defaultSupplier.get(), elementValidator)
                  .orElseThrow(() -> new IllegalArgumentException("Invalid list default for " + name));
            return define(name, defaultValue, raw -> normalizeList(raw, elementValidator));
        }

        public ConfigValue<Byte> defineInRange(String name, byte defaultValue, byte minimum, byte maximum) {
            if (minimum > maximum || defaultValue < minimum || defaultValue > maximum) {
                throw new IllegalArgumentException("Invalid byte range/default for " + name);
            }
            return define(name, defaultValue, raw -> normalizeByte(raw, minimum, maximum));
        }

        public ConfigValue<Short> defineInRange(String name, short defaultValue, short minimum, short maximum) {
            if (minimum > maximum || defaultValue < minimum || defaultValue > maximum) {
                throw new IllegalArgumentException("Invalid short range/default for " + name);
            }
            return define(name, defaultValue, raw -> normalizeShort(raw, minimum, maximum));
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
            return define(name, defaultValue, normalizer, value -> value);
        }

        private <T> ConfigValue<T> define(String name, T defaultValue, Function<Object, Optional<T>> normalizer,
              Function<T, Object> encoder) {
            return defineValue(name, defaultValue, normalizer, encoder, false);
        }

        @SuppressWarnings("unchecked")
        private <T extends Enum<T>> EnumValue<T> defineEnumValue(String name, T defaultValue,
              Function<Object, Optional<T>> normalizer, Function<T, Object> encoder) {
            return (EnumValue<T>) defineValue(name, defaultValue, normalizer, encoder, true);
        }

        private <T> ConfigValue<T> defineValue(String name, T defaultValue, Function<Object, Optional<T>> normalizer,
              Function<T, Object> encoder, boolean enumValue) {
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
            ConfigValue<T> value = enumValue
                  ? new EnumValue(valuePath, (Enum) defaultValue, (Function) normalizer, (Function) encoder,
                        pendingTranslation, pendingComment, pendingRestart)
                  : new ConfigValue<>(valuePath, defaultValue, normalizer, encoder,
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

        private static Optional<Boolean> normalizeBoolean(Object raw) {
            return raw instanceof Boolean value ? Optional.of(value) : Optional.empty();
        }

        private static <T> Optional<List<? extends T>> normalizeList(Object raw, Predicate<Object> elementValidator) {
            if (!(raw instanceof List<?> values)) {
                return Optional.empty();
            }
            List<T> normalized = new ArrayList<>(values.size());
            for (Object element : values) {
                if (element == null || !elementValidator.test(element)) {
                    return Optional.empty();
                }
                @SuppressWarnings("unchecked")
                T typedElement = (T) element;
                normalized.add(typedElement);
            }
            return Optional.of(List.copyOf(normalized));
        }

        private static Optional<Byte> normalizeByte(Object raw, byte minimum, byte maximum) {
            return normalizeLong(raw, minimum, maximum).map(Long::byteValue);
        }

        private static Optional<Short> normalizeShort(Object raw, short minimum, short maximum) {
            return normalizeLong(raw, minimum, maximum).map(Long::shortValue);
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

        private static Optional<Integer> normalizeStrictInteger(Object raw) {
            final long value;
            if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
                value = ((Number) raw).longValue();
            } else if (raw instanceof BigInteger integer) {
                try {
                    value = integer.longValueExact();
                } catch (ArithmeticException ignored) {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
            return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? Optional.of((int) value) : Optional.empty();
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
                    value = new BigDecimal((double) floating).longValueExact();
                } else if (raw instanceof Double floating) {
                    if (!Double.isFinite(floating)) {
                        return Optional.empty();
                    }
                    value = new BigDecimal(floating).longValueExact();
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
