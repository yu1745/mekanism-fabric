package mekanism.fabric.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;

/**
 * Minimal raw-JSON extractors for constructing {@link BlockModel} subclasses from loader-model JSONs. Vanilla's
 * parent resolution hard-requires every parent to be a {@link BlockModel} ({@code FabricTransmitterBlockModel}
 * documents the same constraint from the transmitter lane), so the adapter loader models must extend BlockModel and
 * seed the constructor from the official JSON. The tiny parsers mirror the private ones inside the audited
 * transmitter model class one-for-one instead of widening that class's surface.
 */
final class FabricModelJson {

    private FabricModelJson() {
    }

    static JsonObject root(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    static ResourceLocation parentId(String json) {
        JsonObject root = root(json);
        if (!root.has("parent") || !root.get("parent").isJsonPrimitive() || !root.getAsJsonPrimitive("parent").isString()) {
            return null;
        }
        return ResourceLocation.tryParse(root.get("parent").getAsString());
    }

    /** Vanilla's exact textures rule: {@code #name} values are references, everything else a block-atlas material. */
    static Map<String, com.mojang.datafixers.util.Either<Material, String>> textures(String json) {
        JsonObject root = root(json);
        Map<String, com.mojang.datafixers.util.Either<Material, String>> textures = new LinkedHashMap<>();
        if (root.has("textures") && root.get("textures").isJsonObject()) {
            JsonObject declared = root.getAsJsonObject("textures");
            for (String name : declared.keySet()) {
                String value = declared.get(name).getAsString();
                if (value.startsWith("#")) {
                    textures.put(name, com.mojang.datafixers.util.Either.right(value.substring(1)));
                } else {
                    textures.put(name, com.mojang.datafixers.util.Either.left(new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.parse(value))));
                }
            }
        }
        return textures;
    }

    static Boolean ambientOcclusion(String json) {
        JsonObject root = root(json);
        return root.has("ambientocclusion") ? BlockModel.fromString(json).hasAmbientOcclusion() : null;
    }

    static BlockModel.GuiLight guiLight(String json) {
        JsonObject root = root(json);
        return root.has("gui_light") ? BlockModel.fromString(json).getGuiLight() : null;
    }

    static net.minecraft.client.renderer.block.model.ItemTransforms transforms(String json) {
        return BlockModel.fromString(json).getTransforms();
    }

    static java.util.List<net.minecraft.client.renderer.block.model.ItemOverride> overrides(String json) {
        return BlockModel.fromString(json).getOverrides();
    }
}
