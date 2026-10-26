package mekanism.fabric.client.model;

import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Unbaked stand-in for the official {@code mekanism:data_based} geometry: the no-data model becomes the static bake
 * (vanilla renders it whenever no tile renderer draws over it) and the property-based (lit) models bake alongside and
 * register into {@link FabricDataBasedModels} keyed by their own model id for the tile renderers.
 */
final class FabricDataBasedUnbakedModel implements UnbakedModel {

    private final ResourceLocation id;
    private final JsonObject root;
    private UnbakedModel noData;
    private UnbakedModel lit;
    private ResourceLocation litId;

    FabricDataBasedUnbakedModel(ResourceLocation id, JsonObject root) {
        this.id = id;
        this.root = root;
    }

    @Override
    public @NotNull Collection<ResourceLocation> getDependencies() {
        return List.of(noDataId(), litId());
    }

    @Override
    public void resolveParents(@NotNull Function<ResourceLocation, UnbakedModel> resolver) {
        noData = resolve(resolver, noDataId());
        lit = resolve(resolver, litId());
    }

    @Override
    public @NotNull BakedModel bake(@NotNull ModelBaker baker, @NotNull Function<Material, TextureAtlasSprite> spriteGetter, @NotNull ModelState modelState) {
        BakedModel noDataBaked = noData.bake(baker, spriteGetter, modelState);
        BakedModel litBaked = lit.bake(baker, spriteGetter, modelState);
        FabricDataBasedModels.registerLit(litId, litBaked);
        return noDataBaked;
    }

    private UnbakedModel resolve(Function<ResourceLocation, UnbakedModel> resolver, ResourceLocation name) {
        UnbakedModel model = resolver.apply(name);
        if (model == null) {
            throw new IllegalStateException("Could not find '" + name + "' while loading data-based model '" + id + "'");
        }
        model.resolveParents(resolver);
        return model;
    }

    private ResourceLocation noDataId() {
        return readPath("no_data");
    }

    private ResourceLocation litId() {
        if (litId == null) {
            String propertyKey = root.keySet().stream().filter(k -> !k.equals("no_data") && !k.equals("loader")).findFirst().orElseThrow();
            litId = readPath(propertyKey);
        }
        return litId;
    }

    private ResourceLocation readPath(String key) {
        return ResourceLocation.parse(GsonHelper.getAsString(root, key));
    }
}
