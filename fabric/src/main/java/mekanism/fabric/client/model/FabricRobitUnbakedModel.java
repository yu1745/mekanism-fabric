package mekanism.fabric.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import mekanism.client.model.robit.RobitModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Unbaked stand-in for the robit models: the elements bake with the missingno placeholder texture (the shipped JSONs
 * declare an unmapped {@code #robit} reference on purpose) and the per-skin texture transform applies at draw time.
 */
final class FabricRobitUnbakedModel implements net.minecraft.client.resources.model.UnbakedModel {

    private static final ResourceLocation ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");

    private final ResourceLocation id;
    private final JsonObject root;

    FabricRobitUnbakedModel(ResourceLocation id, JsonObject root) {
        this.id = id;
        this.root = root;
    }

    @Override
    public @NotNull BakedModel bake(@NotNull ModelBaker baker, @NotNull Function<Material, TextureAtlasSprite> spriteGetter, @NotNull ModelState modelState) {
        List<BlockElement> elements = RobitModel.INSTANCE.read(root);
        TextureAtlasSprite missing = spriteGetter.apply(new Material(ATLAS, MissingTextureAtlasSprite.getLocation()));
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockElement element : elements) {
            for (Map.Entry<Direction, BlockElementFace> faceEntry : element.faces.entrySet()) {
                BlockElementFace face = faceEntry.getValue();
                Direction direction = face.cullForDirection() == null ? null
                      : Direction.rotate(modelState.getRotation().getMatrix(), face.cullForDirection());
                quads.add(BlockModel.bakeFace(element, face, missing, faceEntry.getKey(), modelState));
            }
        }
        return new StaticQuads(quads, missing);
    }

    @Override
    public @NotNull java.util.Collection<ResourceLocation> getDependencies() {
        return List.of();
    }

    @Override
    public void resolveParents(@NotNull Function<ResourceLocation, net.minecraft.client.resources.model.UnbakedModel> resolver) {
    }

    /** The static robit element model; the draw sites apply the skin transform. */
    record StaticQuads(List<BakedQuad> quads, TextureAtlasSprite particle) implements BakedModel {

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
            return quads;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return true;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public @NotNull TextureAtlasSprite getParticleIcon() {
            return particle;
        }

        @Override
        public @NotNull ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        @Override
        public @NotNull net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
            return net.minecraft.client.renderer.block.model.ItemTransforms.NO_TRANSFORMS;
        }
    }
}
