package mekanism.fabric.client.model;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Loud deferral for a shipped loader family this port cannot faithfully bake yet (its official baked form reads
 * NeoForge model data or NeoForge-owned base assets). The model resolves to vanilla's standard missing model, so the
 * affected blocks/items show the honest missing marker instead of wrong visuals, and - unlike letting vanilla parse
 * the file - the broken parent chain (e.g. {@code neoforge:item/bucket}, which only exists in the NeoForge jar) is
 * cut off instead of crashing the resource reload. Every deferred family is counted in the plugin's proof line.
 */
final class FabricDeferredLoaderModel implements UnbakedModel {

    static final FabricDeferredLoaderModel INSTANCE = new FabricDeferredLoaderModel();

    private FabricDeferredLoaderModel() {
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        //Deliberately empty: the official parent chain is exactly what cannot resolve on Fabric.
        return List.of();
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
    }

    @Override
    public BakedModel bake(@Nullable ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        return baker.bake(ModelBakery.MISSING_MODEL_LOCATION, state);
    }
}
