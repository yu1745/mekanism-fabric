package mekanism.fabric.datamaps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mekanism.api.datamaps.MekanismDataMapType;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader for the deliberately bounded ordinary/unconditional data-map format. */
public final class FabricDataMapLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricDataMaps");
    private static final String CONDITIONS = "neoforge:conditions";

    private FabricDataMapLoader() {
    }

    /** Validates catalog uniqueness by registry key and id, while preserving descriptor identity. */
    public static void validateCatalog(Collection<? extends MekanismDataMapType<?, ?>> descriptors) {
        Map<ResourceKey<?>, Set<ResourceLocation>> ids = new HashMap<>();
        for (MekanismDataMapType<?, ?> descriptor : descriptors) {
            if (!ids.computeIfAbsent(descriptor.registryKey(), ignored -> new HashSet<>()).add(descriptor.id())) {
                throw new IllegalStateException("Duplicate Fabric data-map descriptor for " + descriptor.registryKey().location() + " / " + descriptor.id());
            }
        }
    }

    public static <R, T> Result<R, T> load(ResourceManager resources, HolderLookup.Provider registries, MekanismDataMapType<R, T> descriptor) {
        return load(resources, registries, descriptor, List.of(descriptor));
    }

    public static <R, T> Result<R, T> load(ResourceManager resources, HolderLookup.Provider registries,
          MekanismDataMapType<R, T> descriptor, Collection<? extends MekanismDataMapType<?, ?>> catalog) {
        validateCatalog(catalog);
        HolderLookup.RegistryLookup<R> registry = registries.lookup(descriptor.registryKey())
              .orElseThrow(() -> new IllegalStateException("Missing data-map registry " + descriptor.registryKey().location()));
        ResourceLocation file = fileLocation(descriptor);
        Set<ResourceLocation> known = new HashSet<>();
        for (MekanismDataMapType<?, ?> knownDescriptor : catalog) known.add(fileLocation(knownDescriptor));
        int skipped = warnUnknownDescriptors(resources, known, file);
        Map<ResourceKey<R>, T> accumulated = new LinkedHashMap<>();
        for (Resource resource : resources.getResourceStack(file)) {
            final Layer<T> layer;
            try (Reader reader = resource.openAsReader()) {
                layer = decodeLayer(JsonParser.parseReader(reader), descriptor, registries.createSerializationContext(JsonOps.INSTANCE));
            } catch (Exception e) {
                skipped++;
                LOGGER.error("Skipping invalid data-map layer {} from pack {}", file, resource.sourcePackId(), e);
                continue;
            }
            if (layer.replace()) {
                accumulated.clear();
            }
            for (Map.Entry<String, Entry<T>> value : layer.values()) {
                applyValue(registry, descriptor.registryKey(), value.getKey(), value.getValue(), accumulated);
            }
            for (String removal : layer.removals()) {
                applyRemoval(registry, descriptor.registryKey(), removal, accumulated);
            }
        }
        return new Result<>(accumulated, skipped);
    }

    static ResourceLocation fileLocation(MekanismDataMapType<?, ?> descriptor) {
        ResourceLocation registry = descriptor.registryKey().location();
        String folder = registry.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)
              ? registry.getPath()
              : registry.getNamespace() + "/" + registry.getPath();
        return ResourceLocation.fromNamespaceAndPath(descriptor.id().getNamespace(),
              "data_maps/" + folder + "/" + descriptor.id().getPath() + ".json");
    }

    static <T> Layer<T> decodeLayer(JsonElement json, MekanismDataMapType<?, T> descriptor) {
        return decodeLayer(json, descriptor, JsonOps.INSTANCE);
    }

    static <T> Layer<T> decodeLayer(JsonElement json, MekanismDataMapType<?, T> descriptor, DynamicOps<JsonElement> ops) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Data-map file must be an object");
        }
        rejectConditions(json);
        JsonObject object = json.getAsJsonObject();
        boolean replace = readBoolean(object, "replace", false);
        JsonElement valuesElement = object.get("values");
        if (valuesElement == null || !valuesElement.isJsonObject()) {
            throw new IllegalArgumentException("Data-map file requires an object 'values'");
        }
        List<Map.Entry<String, Entry<T>>> values = new ArrayList<>();
        for (Map.Entry<String, JsonElement> raw : valuesElement.getAsJsonObject().entrySet()) {
            validateTarget(raw.getKey());
            values.add(Map.entry(raw.getKey(), decodeEntry(raw.getValue(), descriptor, ops)));
        }
        List<String> removals = new ArrayList<>();
        JsonElement removeElement = object.get("remove");
        if (removeElement != null) {
            if (!removeElement.isJsonArray()) {
                throw new IllegalArgumentException("Ordinary data-map 'remove' must be a string list");
            }
            removeElement.getAsJsonArray().forEach(element -> {
                if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
                    throw new IllegalArgumentException("Ordinary data-map removals must be strings");
                }
                String target = primitive.getAsString();
                validateTarget(target);
                removals.add(target);
            });
        }
        return new Layer<>(replace, values, removals);
    }

    static <T> Entry<T> decodeEntry(JsonElement json, MekanismDataMapType<?, T> descriptor) {
        return decodeEntry(json, descriptor, JsonOps.INSTANCE);
    }

    static <T> Entry<T> decodeEntry(JsonElement json, MekanismDataMapType<?, T> descriptor, DynamicOps<JsonElement> ops) {
        if (json.isJsonObject() && json.getAsJsonObject().has("value")) {
            JsonObject wrapper = json.getAsJsonObject();
            try {
                T wrapped = decodeValue(wrapper.get("value"), descriptor, ops);
                return new Entry<>(wrapped, readBoolean(wrapper, "replace", false));
            } catch (RuntimeException wrapperFailure) {
                // Codec.either tries the ordinary bare-value alternative when the wrapper fails.
                try {
                    return new Entry<>(decodeValue(json, descriptor, ops), false);
                } catch (RuntimeException bareFailure) {
                    bareFailure.addSuppressed(wrapperFailure);
                    throw bareFailure;
                }
            }
        }
        return new Entry<>(decodeValue(json, descriptor, ops), false);
    }

    private static <T> T decodeValue(JsonElement json, MekanismDataMapType<?, T> descriptor, DynamicOps<JsonElement> ops) {
        return descriptor.codec().parse(ops, json)
              .getOrThrow(error -> new IllegalArgumentException("Invalid data-map value: " + error));
    }

    private static boolean readBoolean(JsonObject object, String name, boolean fallback) {
        JsonElement element = object.get(name);
        if (element == null) {
            return fallback;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            throw new IllegalArgumentException("'" + name + "' must be a boolean");
        }
        return primitive.getAsBoolean();
    }

    private static void rejectConditions(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has(CONDITIONS)) {
                throw new IllegalArgumentException("Unsupported conditional data-map input: " + CONDITIONS);
            }
            object.entrySet().forEach(entry -> rejectConditions(entry.getValue()));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(FabricDataMapLoader::rejectConditions);
        }
    }

    private static ResourceLocation validateTarget(String target) {
        String location = target.startsWith("#") ? target.substring(1) : target;
        ResourceLocation parsed = ResourceLocation.tryParse(location);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid data-map target: " + target);
        }
        return parsed;
    }

    private static <R, T> void applyValue(HolderLookup.RegistryLookup<R> registry, ResourceKey<? extends Registry<R>> registryKey,
          String target, Entry<T> entry, Map<ResourceKey<R>, T> accumulated) {
        ResourceLocation location = validateTarget(target);
        if (target.startsWith("#")) {
            registry.get(TagKey.create(registryKey, location)).ifPresent(holders -> {
                for (Holder<R> holder : holders) {
                    holder.unwrapKey().ifPresent(key -> attachOrdinary(accumulated, key, entry));
                }
            });
        } else {
            ResourceKey<R> key = ResourceKey.create(registryKey, location);
            if (registry.get(key).isPresent()) {
                attachOrdinary(accumulated, key, entry);
            } else {
                LOGGER.error("Skipping unknown data-map value target {} in registry {}", location, registryKey.location());
            }
        }
    }

    private static <R, T> void attachOrdinary(Map<ResourceKey<R>, T> accumulated, ResourceKey<R> key, Entry<T> entry) {
        // Ordinary descriptors have no merger: parsed entry replace remains syntax-only and every write replaces.
        accumulated.put(key, entry.value());
    }

    private static <R, T> void applyRemoval(HolderLookup.RegistryLookup<R> registry, ResourceKey<? extends Registry<R>> registryKey,
          String target, Map<ResourceKey<R>, T> accumulated) {
        ResourceLocation location = validateTarget(target);
        if (target.startsWith("#")) {
            registry.get(TagKey.create(registryKey, location)).ifPresent(holders -> {
                for (Holder<R> holder : holders) {
                    holder.unwrapKey().ifPresent(accumulated::remove);
                }
            });
        } else {
            accumulated.remove(ResourceKey.create(registryKey, location));
        }
    }

    private static int warnUnknownDescriptors(ResourceManager resources, Set<ResourceLocation> known, ResourceLocation expectedFile) {
        int skipped = 0;
        String path = expectedFile.getPath();
        String registryFolder = path.substring(0, path.lastIndexOf('/') + 1);
        for (Map.Entry<ResourceLocation, List<Resource>> entry : resources.listResourceStacks("data_maps", id ->
              id.getNamespace().equals(expectedFile.getNamespace()) && id.getPath().startsWith(registryFolder)
                    && id.getPath().endsWith(".json")).entrySet()) {
            if (!known.contains(entry.getKey())) {
                skipped += entry.getValue().size();
                LOGGER.warn("Skipping unknown Fabric data-map descriptor file {} ({} layer(s))", entry.getKey(), entry.getValue().size());
            }
        }
        return skipped;
    }

    public record Entry<T>(T value, boolean replace) {
    }

    public record Layer<T>(boolean replace, List<Map.Entry<String, Entry<T>>> values, List<String> removals) {
        public Layer {
            values = List.copyOf(values);
            removals = List.copyOf(removals);
        }
    }

    public record Result<R, T>(Map<ResourceKey<R>, T> values, int skippedLayers) {
        public Result {
            values = Map.copyOf(values);
            if (skippedLayers < 0) {
                throw new IllegalArgumentException("skippedLayers must be nonnegative");
            }
        }
    }
}
