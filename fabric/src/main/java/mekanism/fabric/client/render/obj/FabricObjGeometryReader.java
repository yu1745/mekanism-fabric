package mekanism.fabric.client.render.obj;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reads the OBJ and MTL sources a {@code neoforge:obj} model declares out of the real resource pack and hands them to
 * the shipped {@link TransmitterObjGeometry} parser. The meka-tool entity OBJs reference their textures through
 * {@code mtllib}/{@code map_Kd} pairs (unlike the transmitter OBJs, which alias through the model JSON), so the MTL
 * sources are part of the general input here.
 */
public final class FabricObjGeometryReader {

    private FabricObjGeometryReader() {
    }

    /**
     * Parses the OBJ declared by the model's {@code model} field plus its {@code mtllib} chain.
     *
     * @param id the owning model id, used for error messages
     * @param def the {@code neoforge:obj} definition ({@code model}, optional {@code flip_v})
     */
    public static TransmitterObjGeometry read(ResourceManager resources, ResourceLocation id, JsonObject def) {
        if (!def.has("model") || !def.get("model").isJsonPrimitive()) {
            throw new IllegalStateException("neoforge:obj model declares no model path: " + id);
        }
        ResourceLocation objPath = ResourceLocation.parse(def.get("model").getAsString());
        boolean flipV = def.has("flip_v") && def.get("flip_v").getAsBoolean();
        String objSource = readResource(resources, id, objPath);
        Map<String, String> mtlSources = new LinkedHashMap<>();
        for (String line : objSource.split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2 && parts[0].equals("mtllib")) {
                //Relative to the OBJ's own directory (which already includes the models/ root of its resource id),
                //mirroring how the official loader resolves the declaration.
                String directory = objPath.getPath().contains("/")
                      ? objPath.getPath().substring(0, objPath.getPath().lastIndexOf('/') + 1)
                      : "";
                ResourceLocation mtlId = parts[1].contains(":")
                      ? ResourceLocation.parse(parts[1])
                      : ResourceLocation.fromNamespaceAndPath(objPath.getNamespace(), directory + parts[1]);
                mtlSources.put(parts[1], readResource(resources, id, mtlId));
            }
        }
        return TransmitterObjGeometry.parse(objSource, mtlSources, flipV);
    }

    private static String readResource(ResourceManager resources, ResourceLocation modelId, ResourceLocation path) {
        Resource resource = resources.getResource(path).orElseThrow(
              () -> new IllegalStateException("Mekanism model " + modelId + " references missing resource " + path));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
            StringBuilder source = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                source.append(line).append('\n');
            }
            return source.toString();
        } catch (IOException failure) {
            throw new IllegalStateException("Mekanism model " + modelId + " references unreadable resource " + path, failure);
        }
    }
}
