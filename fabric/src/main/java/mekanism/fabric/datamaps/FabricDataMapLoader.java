package mekanism.fabric.datamaps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
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
    public static void validateCatalog(Collection<? extends FabricDataMapType<?, ?>> descriptors) {
        Map<ResourceKey<?>, Set<ResourceLocation>> ids = new HashMap<>();
        for (FabricDataMapType<?, ?> descriptor : descriptors) {
            if (!ids.computeIfAbsent(descriptor.registryKey(), ignored -> new HashSet<>()).add(descriptor.id())) {
                throw new IllegalStateException("Duplicate Fabric data-map descriptor for " + descriptor.registryKey().location() + " / " + descriptor.id());
            }
        }
    }

    public static <R, T> Result<R, T> load(ResourceManager resources, HolderLookup.Provider registries, FabricDataMapType<R, T> descriptor) {
        validateCatalog(List.of(descriptor));
        HolderLookup.RegistryLookup<R> registry = registries.lookup(descriptor.registryKey())
              .orElseThrow(() -> new IllegalStateException("Missing data-map registry " + descriptor.registryKey().location()));
        ResourceLocation file = fileLocation(descriptor);
        int skipped = warnUnknownDescriptors(resources, Set.of(file));
        Map<ResourceKey<R>, T> accumulated = new LinkedHashMap<>();
        for (Resource resource : resources.getResourceStack(file)) {
            final Layer<T> layer;
            try (Reader reader = resource.openAsReader()) {
                layer = decodeLayer(JsonParser.parseReader(reader), descriptor);
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

    static ResourceLocation fileLocation(FabricDataMapType<?, ?> descriptor) {
        ResourceLocation registry = descriptor.registryKey().location();
        String folder = registry.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)
              ? registry.getPath()
              : registry.getNamespace() + "/" + registry.getPath();
        return ResourceLocation.fromNamespaceAndPath(descriptor.id().getNamespace(),
              "data_maps/" + folder + "/" + descriptor.id().getPath() + ".json");
    }

    static <T> Layer<T> decodeLayer(JsonElement json, FabricDataMapType<?, T> descriptor) {
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
            values.add(Map.entry(raw.getKey(), decodeEntry(raw.getValue(), descriptor)));
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

    static <T> Entry<T> decodeEntry(JsonElement json, FabricDataMapType<?, T> descriptor) {
        if (json.isJsonObject() && json.getAsJsonObject().has("value")) {
            JsonObject wrapper = json.getAsJsonObject();
            try {
                T wrapped = decodeValue(wrapper.get("value"), descriptor);
                return new Entry<>(wrapped, readBoolean(wrapper, "replace", false));
            } catch (RuntimeException wrapperFailure) {
                // Codec.either tries the ordinary bare-value alternative when the wrapper fails.
                try {
                    return new Entry<>(decodeValue(json, descriptor), false);
                } catch (RuntimeException bareFailure) {
                    bareFailure.addSuppressed(wrapperFailure);
                    throw bareFailure;
                }
            }
        }
        return new Entry<>(decodeValue(json, descriptor), false);
    }

    private static <T> T decodeValue(JsonElement json, FabricDataMapType<?, T> descriptor) {
        return descriptor.codec().parse(JsonOps.INSTANCE, json)
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
                LOGGER.warn("Skipping unknown data-map value target {} in registry {}", location, registryKey.location());
            }
        }
    }

    private static <R, T> void attachOrdinary(Map<ResourceKey<R>, T> accumulated, ResourceKey<R> key, Entry<T> entry) {
        if (entry.replace() || !accumulated.containsKey(key)) {
            accumulated.put(key, entry.value());
        } else {
            // Ordinary descriptors have no merger: an existing value is replaced by the incoming value.
            accumulated.put(key, entry.value());
        }
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

    private static int warnUnknownDescriptors(ResourceManager resources, Set<ResourceLocation> known) {
        int skipped = 0;
        for (Map.Entry<ResourceLocation, List<Resource>> entry : resources.listResourceStacks("data_maps", id -> id.getPath().endsWith(".json")).entrySet()) {
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
