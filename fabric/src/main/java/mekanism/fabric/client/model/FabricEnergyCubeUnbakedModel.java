package mekanism.fabric.client.model;

import com.google.gson.JsonObject;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Unbaked stand-in for an energy cube model: vanilla calls {@link #bake} with the blocks-atlas sprite getter once
 * sprites are stitched, which is exactly when the reshaped official geometry can resolve its textures.
 */
final class FabricEnergyCubeUnbakedModel implements net.minecraft.client.resources.model.UnbakedModel {

    private final ResourceLocation id;
    private final JsonObject root;
    @Nullable
    private final JsonObject parentTextures;

    FabricEnergyCubeUnbakedModel(ResourceLocation id, JsonObject root, @Nullable JsonObject parentTextures) {
        this.id = id;
        this.root = root;
        this.parentTextures = parentTextures;
    }

    @Override
    public @NotNull BakedModel bake(@NotNull ModelBaker baker, @NotNull Function<Material, TextureAtlasSprite> spriteGetter, @NotNull ModelState modelState) {
        return FabricEnergyCubeModel.bake(id, root, parentTextures, spriteGetter);
    }

    @Override
    public @NotNull java.util.Collection<ResourceLocation> getDependencies() {
        return java.util.List.of();
    }

    @Override
    public void resolveParents(@NotNull Function<ResourceLocation, net.minecraft.client.resources.model.UnbakedModel> resolver) {
        //The merged texture map was resolved at parse time; no parent models to walk.
    }
}
