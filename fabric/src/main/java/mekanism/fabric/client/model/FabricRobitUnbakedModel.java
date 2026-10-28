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
    private final BlockModel properties;
    @Nullable
    private final ResourceLocation parentId;
    @Nullable
    private net.minecraft.client.resources.model.UnbakedModel parent;
    private boolean resolving;

    FabricRobitUnbakedModel(ResourceLocation id, JsonObject root) {
        this.id = id;
        this.root = root;
        this.properties = BlockModel.fromString(root.toString());
        this.parentId = root.has("parent") ? ResourceLocation.parse(root.get("parent").getAsString()) : null;
    }

    @Override
    public @NotNull BakedModel bake(@NotNull ModelBaker baker, @NotNull Function<Material, TextureAtlasSprite> spriteGetter, @NotNull ModelState modelState) {
        List<BlockElement> elements = RobitModel.INSTANCE.read(root);
        TextureAtlasSprite missing = spriteGetter.apply(new Material(ATLAS, MissingTextureAtlasSprite.getLocation()));
        List<BakedQuad> quads = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            BlockElement element = elements.get(i);
            JsonObject raw = root.getAsJsonArray("elements").get(i).getAsJsonObject();
            JsonObject lightData = raw.has("neoforge_data") ? raw.getAsJsonObject("neoforge_data") : null;
            int block = lightData == null ? 0 : net.minecraft.util.GsonHelper.getAsInt(lightData, "block_light", 0);
            int sky = lightData == null ? 0 : net.minecraft.util.GsonHelper.getAsInt(lightData, "sky_light", 0);
            int minimumLight = net.minecraft.client.renderer.LightTexture.pack(block, sky);
            for (Map.Entry<Direction, BlockElementFace> faceEntry : element.faces.entrySet()) {
                BlockElementFace face = faceEntry.getValue();
                TextureAtlasSprite sprite = spriteGetter.apply(material(face.texture(), new java.util.HashSet<>()));
                BakedQuad quad = BlockModel.bakeFace(element, face, sprite, faceEntry.getKey(), modelState);
                for (int vertex = 0; vertex < 4; vertex++) {
                    quad.getVertices()[vertex * 8 + 6] = minimumLight;
                }
                quads.add(quad);
            }
        }
        ItemOverrides overrides = properties.getOverrides().isEmpty() ? ItemOverrides.EMPTY : new ItemOverrides(baker, properties, properties.getOverrides());
        return new StaticQuads(quads, missing, transforms(), overrides);
    }

    @Override
    public @NotNull java.util.Collection<ResourceLocation> getDependencies() {
        return properties.getDependencies();
    }

    @Override
    public void resolveParents(@NotNull Function<ResourceLocation, net.minecraft.client.resources.model.UnbakedModel> resolver) {
        if (parentId == null || parent != null) {
            return;
        }
        if (resolving) {
            throw new IllegalStateException("Robit model parent cycle at " + id);
        }
        resolving = true;
        try {
            var resolved = resolver.apply(parentId);
            resolved.resolveParents(resolver);
            parent = resolved;
        } finally {
            resolving = false;
        }
    }

    private Material material(String reference, java.util.Set<String> seen) {
        while (reference.startsWith("#")) {
            String key = reference.substring(1);
            if (!seen.add(key)) {
                throw new IllegalStateException("Robit model texture cycle at " + id + ": " + key);
            }
            String value = textureAlias(key);
            if (value == null) {
                if (parent instanceof BlockModel block) {
                    return block.getMaterial(reference);
                }
                return new Material(ATLAS, MissingTextureAtlasSprite.getLocation());
            }
            reference = value;
        }
        return new Material(ATLAS, ResourceLocation.parse(reference));
    }

    @Nullable
    private String textureAlias(String key) {
        if (root.has("textures") && root.getAsJsonObject("textures").has(key)) {
            return root.getAsJsonObject("textures").get(key).getAsString();
        }
        return parent instanceof FabricRobitUnbakedModel robit ? robit.textureAlias(key) : null;
    }

    private net.minecraft.client.renderer.block.model.ItemTransforms transforms() {
        var own = properties.getTransforms();
        var inherited = parent instanceof FabricRobitUnbakedModel robit ? robit.transforms()
              : parent instanceof BlockModel block ? block.getTransforms() : net.minecraft.client.renderer.block.model.ItemTransforms.NO_TRANSFORMS;
        return new net.minecraft.client.renderer.block.model.ItemTransforms(
              select(own.thirdPersonLeftHand, inherited.thirdPersonLeftHand), select(own.thirdPersonRightHand, inherited.thirdPersonRightHand),
              select(own.firstPersonLeftHand, inherited.firstPersonLeftHand), select(own.firstPersonRightHand, inherited.firstPersonRightHand),
              select(own.head, inherited.head), select(own.gui, inherited.gui), select(own.ground, inherited.ground), select(own.fixed, inherited.fixed));
    }

    private static net.minecraft.client.renderer.block.model.ItemTransform select(net.minecraft.client.renderer.block.model.ItemTransform own,
          net.minecraft.client.renderer.block.model.ItemTransform inherited) {
        return own == net.minecraft.client.renderer.block.model.ItemTransform.NO_TRANSFORM ? inherited : own;
    }

    /** The static robit element model; the draw sites apply the skin transform. */
    record StaticQuads(List<BakedQuad> quads, TextureAtlasSprite particle, net.minecraft.client.renderer.block.model.ItemTransforms transforms,
                       ItemOverrides overrides) implements BakedModel {

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
            return overrides;
        }

        @Override
        public @NotNull net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
            return transforms;
        }
    }
}
