package mekanism.fabric.client.render.obj;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

/**
 * Discovers which shipped models are transmitter models by reading the real resource pack, rather than hard-coding an id
 * list. Two kinds are found:
 *
 * <ul>
 *   <li><b>bases</b>: JSONs that declare {@code "loader": "mekanism:transmitter"} (the transmitter bases whose geometry
 *       and glass assets the other models reuse);</li>
 *   <li><b>descendants</b>: models whose {@code parent} chain reaches a base (the tier block models, which add the real
 *       texture aliases, and the transmitter item models, whose parent is a tier model).</li>
 * </ul>
 *
 * <p>The scan is bounded to {@code models/block/transmitter/**} and {@code models/item/**} - the only places the shipped
 * transmitter models live - and runs in the off-thread preparable phase.</p>
 */
final class FabricTransmitterModels {

    private FabricTransmitterModels() {
    }

    /**
     * @return every transmitter model id mapped to its nearest transmitter base ancestor, plus the bases themselves
     *       mapped to themselves.
     */
    static Map<ResourceLocation, ResourceLocation> discover(ResourceManager resources) {
        Map<ResourceLocation, String> parents = new LinkedHashMap<>();
        Set<ResourceLocation> bases = new LinkedHashSet<>();
        for (Map.Entry<ResourceLocation, Resource> entry : candidateJsons(resources).entrySet()) {
            ResourceLocation id = modelId(entry.getKey());
            String json = read(entry.getValue());
            if (isTransmitterLoader(json)) {
                bases.add(id);
            }
            parents.put(id, parentPath(json));
        }
        Map<ResourceLocation, ResourceLocation> result = new LinkedHashMap<>();
        for (ResourceLocation id : parents.keySet()) {
            ResourceLocation base = nearestBase(id, parents, bases);
            if (base != null) {
                result.put(id, base);
            }
        }
        return result;
    }

    static boolean isTransmitterLoader(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonElement loader = root.get("loader");
        if (loader == null) {
            return false;
        }
        if (!loader.isJsonPrimitive() || !loader.getAsJsonPrimitive().isString()) {
            throw new com.google.gson.JsonParseException("Model loader must be a string");
        }
        return FabricTransmitterModelPlugin.LOADER_ID.equals(loader.getAsString());
    }

    @Nullable
    private static ResourceLocation nearestBase(ResourceLocation id, Map<ResourceLocation, String> parents, Set<ResourceLocation> bases) {
        ResourceLocation current = id;
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        while (current != null && seen.add(current)) {
            if (bases.contains(current)) {
                return current;
            }
            String parentPath = parents.get(current);
            if (parentPath == null || !parents.containsKey(ResourceLocation.parse(parentPath))) {
                //A parent outside the scanned candidate set cannot be a transmitter base (all bases are declared in it).
                return null;
            }
            current = ResourceLocation.parse(parentPath);
        }
        return null;
    }

    /** Bounded scan: only the block/transmitter tree and the item models can hold transmitter models. */
    private static Map<ResourceLocation, Resource> candidateJsons(ResourceManager resources) {
        Map<ResourceLocation, Resource> candidates = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resources.listResources("models", id -> id.getPath().endsWith(".json")).entrySet()) {
            String path = entry.getKey().getPath();
            if (!path.startsWith("models/")) {
                continue;
            }
            String modelPath = path.substring("models/".length());
            if (modelPath.startsWith("block/transmitter/") || modelPath.startsWith("item/")) {
                candidates.put(entry.getKey(), entry.getValue());
            }
        }
        return candidates;
    }

    /**
     * Strips the {@code .json} suffix AND the {@code models/} root, because a model id (what
     * {@code ModelResolver.Context#id()} reports) is the path below {@code models/}. Getting this wrong would make the
     * plugin's lookup miss every model.
     */
    private static ResourceLocation modelId(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        if (path.startsWith("models/")) {
            path = path.substring("models/".length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - ".json".length());
        }
        return ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), path);
    }

    @Nullable
    private static String parentPath(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("parent") || !object.get("parent").isJsonPrimitive() || !object.getAsJsonPrimitive("parent").isString()) {
            return null;
        }
        return object.get("parent").getAsString();
    }

    private static String read(Resource resource) {
        try (BufferedReader reader = resource.openAsReader()) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read transmitter candidate model resource", e);
        }
    }
}
