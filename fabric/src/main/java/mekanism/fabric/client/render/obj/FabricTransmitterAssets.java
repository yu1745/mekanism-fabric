package mekanism.fabric.client.render.obj;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

/**
 * Off-thread reader for the shipped {@code "loader": "mekanism:transmitter"} assets: the narrow Mekanism transmitter
 * replacement for the NeoForge {@code TransmitterLoader}. Every OBJ/MTL/JSON resource is read from the real
 * {@link ResourceManager} during the preparable plugin phase, so no client-runtime lookup happens at parse time.
 *
 * <p>Supported keys are exactly the ones the shipped assets use: {@code model}, {@code flip_v}, {@code glass.model},
 * {@code glass.flip_v} and {@code glass.mtl_override}. The remaining keys ({@code parent}, {@code textures},
 * {@code display}) belong to the vanilla {@link net.minecraft.client.renderer.block.model.BlockModel} chain and are
 * handled there, not re-implemented here.</p>
 */
public final class FabricTransmitterAssets {

    /**
     * Everything read for one transmitter loader JSON.
     *
     * @param json      the raw loader JSON, used to build the vanilla {@code BlockModel} delegate
     * @param obj       the parsed internal geometry assets (the {@code model} key)
     * @param glass     the parsed glass geometry assets, or {@code null} when the JSON declares no glass pass
     * @param resources the exact raw resource ids read by preparation, NOT model dependencies
     */
    public record Assets(String json, ObjAssets obj, @Nullable ObjAssets glass, Set<ResourceLocation> resources) {
    }

    /**
     * One {@code .obj.mek} plus every MTL library its {@code mtllib} directive names. {@code mtlSources} is keyed by the
     * bare name the OBJ declares, so an {@code mtl_override} replaces that library's content while the OBJ text (and
     * therefore the declared name) stays untouched - the semantic NeoForge's OBJ loader implements.
     */
    public record ObjAssets(ResourceLocation objId, boolean flipV, String objSource, Map<String, String> mtlSources) {
    }

    /** One {@code mtllib} entry: the bare name written in the OBJ and where its content is actually read from. */
    public record DeclaredLibrary(String declaredName, ResourceLocation resolvedId) {
    }

    private FabricTransmitterAssets() {
    }

    /** Reads the loader JSON and every OBJ/MTL resource it references; throws rather than silently dropping geometry. */
    public static Assets read(ResourceManager resources, ResourceLocation modelId) throws IOException {
        ResourceLocation jsonId = jsonId(modelId);
        JsonObject root = readJson(resources, jsonId);
        if (!FabricTransmitterModels.isTransmitterLoader(root.toString())) {
            throw new JsonParseException("Transmitter model " + modelId + " must declare loader mekanism:transmitter");
        }
        Set<ResourceLocation> dependencies = new LinkedHashSet<>();
        dependencies.add(jsonId);
        ObjAssets internal = readObj(resources, requireId(root, "model", modelId), bool(root, "flip_v", false), null, dependencies);
        ObjAssets glass = null;
        if (root.has("glass")) {
            JsonObject glassJson = requireObject(root, "glass", modelId);
            ResourceLocation override = glassJson.has("mtl_override") ? requireId(glassJson, "mtl_override", modelId) : null;
            glass = readObj(resources, requireId(glassJson, "model", modelId), bool(glassJson, "flip_v", false), override, dependencies);
        }
        return new Assets(readString(resources, jsonId), internal, glass, dependencies);
    }

    /** The {@code models/<path>.json} resource id a bare model id resolves to. */
    public static ResourceLocation jsonId(ResourceLocation modelId) {
        return ResourceLocation.fromNamespaceAndPath(modelId.getNamespace(), "models/" + modelId.getPath() + ".json");
    }

    /** The raw loader JSON text for a model id, or {@code null} when that model has no transmitter loader JSON. */
    @Nullable
    public static String tryReadJsonText(ResourceManager resources, ResourceLocation modelId) {
        return resources.getResource(jsonId(modelId)).map(resource -> {
            try (BufferedReader reader = resource.openAsReader()) {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) >= 0) {
                    builder.append(buffer, 0, read);
                }
                return builder.toString();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read transmitter model JSON " + modelId, e);
            }
        }).orElse(null);
    }

    private static ObjAssets readObj(ResourceManager resources, ResourceLocation objId, boolean flipV, @Nullable ResourceLocation mtlOverride,
          Set<ResourceLocation> dependencies) throws IOException {
        dependencies.add(objId);
        String objSource = readString(resources, objId);
        List<DeclaredLibrary> libraries = declaredLibraries(objId, objSource, mtlOverride);
        List<String> contents = new ArrayList<>(libraries.size());
        for (DeclaredLibrary library : libraries) {
            dependencies.add(library.resolvedId());
            contents.add(readString(resources, library.resolvedId()));
        }
        return new ObjAssets(objId, flipV, objSource, libraryMap(libraries, contents));
    }

    /**
     * Resolves every {@code mtllib} an OBJ declares. A bare name is resolved against the OBJ's own directory, as
     * NeoForge's OBJ loader does; when {@code mtlOverride} is present it becomes the source of every declared library.
     */
    public static List<DeclaredLibrary> declaredLibraries(ResourceLocation objId, String objSource, @Nullable ResourceLocation mtlOverride) {
        String path = objId.getPath();
        int slash = path.lastIndexOf('/');
        String directory = slash < 0 ? "" : path.substring(0, slash + 1);
        List<DeclaredLibrary> libraries = new ArrayList<>();
        for (String raw : objSource.split("\n", -1)) {
            String line = raw.trim();
            if (!line.startsWith("mtllib ")) {
                continue;
            }
            for (String name : line.substring("mtllib ".length()).trim().split("\\s+")) {
                if (!name.isEmpty()) {
                    libraries.add(new DeclaredLibrary(name,
                          mtlOverride != null ? mtlOverride : ResourceLocation.fromNamespaceAndPath(objId.getNamespace(), directory + name)));
                }
            }
        }
        return libraries;
    }

    /** Libraries keyed by the OBJ-declared names, so the parser must find every declared library. */
    private static Map<String, String> libraryMap(List<DeclaredLibrary> libraries, List<String> contents) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < libraries.size(); i++) {
            map.put(libraries.get(i).declaredName(), contents.get(i));
        }
        return map;
    }

    private static JsonObject readJson(ResourceManager resources, ResourceLocation id) throws IOException {
        try (BufferedReader reader = resources.getResourceOrThrow(id).openAsReader()) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new JsonParseException("Transmitter model " + id + " must be a JSON object");
            }
            return element.getAsJsonObject();
        }
    }

    private static String readString(ResourceManager resources, ResourceLocation id) throws IOException {
        Resource resource = resources.getResourceOrThrow(id);
        try (BufferedReader reader = resource.openAsReader()) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }

    private static ResourceLocation requireId(JsonObject json, String key, ResourceLocation modelId) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString()) {
            throw new JsonParseException("Transmitter model " + modelId + " requires a string '" + key + "'");
        }
        return ResourceLocation.parse(json.get(key).getAsString());
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) {
            return fallback;
        }
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean()) {
            throw new JsonParseException("Transmitter model key '" + key + "' must be a boolean");
        }
        return json.get(key).getAsBoolean();
    }

    private static JsonObject requireObject(JsonObject json, String key, ResourceLocation modelId) {
        if (!json.has(key) || !json.get(key).isJsonObject()) {
            throw new JsonParseException("Transmitter model " + modelId + " key '" + key + "' must be an object");
        }
        return json.getAsJsonObject(key);
    }
}
