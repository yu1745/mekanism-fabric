package mekanism.fabric.client.model;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * {@code neoforge:item_layers} semantics: the shipped portable QIO dashboard item is the vanilla generated-item layer
 * stack, so the adapter rebases the official JSON onto {@code minecraft:item/generated} and delegates the entire
 * bake to the vanilla pipeline.
 *
 * <p>Documented visual delta: NeoForge's per-layer full-brightness data ({@code neoforge_data.layers}) has no
 * vanilla 1.21.1 counterpart, so the dashboard LED layer renders with scene lighting instead of being forced
 * full-bright. The official JSON stays the source of truth and is not modified.</p>
 *
 * <p>Extends BlockModel for the same vanilla parent-resolution requirement documented on the composite model.</p>
 */
public final class FabricItemLayersModel extends BlockModel {

    FabricItemLayersModel(ResourceLocation id, JsonObject root) {
        this(rebasedJson(root), id);
    }

    private FabricItemLayersModel(String rebased, ResourceLocation id) {
        super(FabricModelJson.parentId(rebased), List.<BlockElement>of(), FabricModelJson.textures(rebased),
              FabricModelJson.ambientOcclusion(rebased), FabricModelJson.guiLight(rebased),
              FabricModelJson.transforms(rebased), FabricModelJson.overrides(rebased));
        this.name = id.toString();
    }

    private static String rebasedJson(JsonObject root) {
        if (!root.has("textures") || !root.get("textures").isJsonObject()) {
            throw new IllegalStateException("item_layers model declares no textures");
        }
        JsonObject rebased = new JsonObject();
        rebased.addProperty("parent", "minecraft:item/generated");
        rebased.add("textures", root.getAsJsonObject("textures"));
        return rebased.toString();
    }

    @Override
    public BakedModel bake(@Nullable ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        return super.bake(baker, spriteGetter, state);
    }
}
