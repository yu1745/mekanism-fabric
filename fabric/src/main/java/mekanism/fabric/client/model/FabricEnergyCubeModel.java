package mekanism.fabric.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import mekanism.client.model.energycube.EnergyCubeBakedModel;
import mekanism.client.model.energycube.EnergyCubeGeometry;
import mekanism.client.model.energycube.EnergyCubeModelLoader;
import mekanism.common.tile.TileEntityEnergyCube;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric closure for the official {@code mekanism:energy_cube} model family. The shipped geometry carrier
 * ({@code block/energy_cube/base.json}) declares the loader; the five tier models parent onto it and override only the
 * textures. The plugin parses each tier's merged texture map through the reshaped official loader/geometry, then:
 *
 * <ul>
 * <li>the baked block model carries the static frame plus the all-inactive LED set (the official default state) -
 * the ports and the lit LEDs draw from {@link mekanism.client.render.tileentity.RenderEnergyCube} driven by the
 * tile's render data, because the per-instance side selection has no fabric model-data counterpart;</li>
 * <li>the same geometry instance feeds the BER's dynamic split (lit LEDs + active ports at full brightness, unlit
 * LEDs and input ports at world light).</li>
 * </ul>
 */
public final class FabricEnergyCubeModel implements BakedModel {

    private static final ResourceLocation ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    /** Identity rotation, matching the shipped JSONs (no root transform). */
    private static final ModelState IDENTITY = new ModelState() {
    };

    private final EnergyCubeBakedModel dynamic;
    private final List<BakedQuad> staticQuads;
    private final TextureAtlasSprite particle;

    private FabricEnergyCubeModel(EnergyCubeBakedModel dynamic, List<BakedQuad> staticQuads, TextureAtlasSprite particle) {
        this.dynamic = dynamic;
        this.staticQuads = staticQuads;
        this.particle = particle;
    }

    @Nullable
    public EnergyCubeBakedModel dynamic() {
        return dynamic;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        return staticQuads;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
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
    public @NotNull net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
        return net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY;
    }

    @Override
    public @NotNull net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
        return net.minecraft.client.renderer.block.model.ItemTransforms.NO_TRANSFORMS;
    }

    /**
     * Parses one energy cube model JSON (the geometry carrier or a tier child) against the merged parent+child
     * texture map and bakes it once sprites are stitched. Returns the baked model and registers the dynamic quad
     * source under {@code id} for the BER.
     */
    public static FabricEnergyCubeModel bake(ResourceLocation id, JsonObject root, @Nullable JsonObject parentTextures,
          Function<Material, TextureAtlasSprite> spriteGetter) {
        EnergyCubeGeometry geometry = EnergyCubeModelLoader.INSTANCE.read(root);
        Map<String, String> textures = new HashMap<>();
        if (parentTextures != null) {
            for (Map.Entry<String, JsonElement> entry : parentTextures.getAsJsonObject().entrySet()) {
                textures.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        if (root.has("textures")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("textures").entrySet()) {
                textures.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        Function<String, TextureAtlasSprite> rawSpriteGetter = ref -> {
            String resolved = textures.get(ref.startsWith("#") ? ref.substring(1) : ref);
            if (resolved == null) {
                throw new IllegalStateException("Energy cube model " + id + " references unresolved texture: " + ref);
            }
            return spriteGetter.apply(new Material(ATLAS, ResourceLocation.parse(resolved)));
        };
        String particleRef = textures.getOrDefault("particle", "missing");
        TextureAtlasSprite particle = spriteGetter.apply(new Material(ATLAS, ResourceLocation.parse(particleRef)));

        EnergyCubeBakedModel dynamic = geometry.bake(rawSpriteGetter, IDENTITY, particle);
        //The static block model: the frame plus the all-inactive LED set - the official default selection.
        List<BakedQuad> staticQuads = new ArrayList<>(dynamic.getFrameQuads(null));
        for (Direction side : Direction.values()) {
            staticQuads.addAll(dynamic.getFrameQuads(side));
        }
        EnergyCubeBakedModel.QuadSplits inactive = dynamic.getInactiveQuads(null);
        staticQuads.addAll(inactive.unlit());
        for (Direction side : Direction.values()) {
            staticQuads.addAll(dynamic.getInactiveQuads(side).unlit());
        }
        FabricEnergyCubeModels.register(id, dynamic);
        return new FabricEnergyCubeModel(dynamic, staticQuads, particle);
    }

    /** Registry the BER reads; keyed by the tier model id ({@code block/energy_cube/<tier>}). */
    public static final class FabricEnergyCubeModels {

        private static final Map<ResourceLocation, EnergyCubeBakedModel> BAKED = new HashMap<>();

        private FabricEnergyCubeModels() {
        }

        static void register(ResourceLocation id, EnergyCubeBakedModel model) {
            BAKED.put(id, model);
        }

        @Nullable
        public static EnergyCubeBakedModel byId(@NotNull ResourceLocation modelId) {
            return BAKED.get(modelId);
        }

        /** Renders the sides for one tile by resolving its tier's model id. */
        public static void renderTier(@NotNull TileEntityEnergyCube tile, @NotNull com.mojang.blaze3d.vertex.PoseStack poseStack,
              @NotNull net.minecraft.client.renderer.MultiBufferSource renderer, int light, int overlay) {
            String tier = tile.getTier().getBaseTier().name().toLowerCase(java.util.Locale.ROOT);
            EnergyCubeBakedModel model = byId(ResourceLocation.fromNamespaceAndPath(mekanism.api.MekanismAPI.MEKANISM_MODID, "block/energy_cube/" + tier));
            if (model != null) {
                renderSides(model, poseStack, renderer.getBuffer(RenderType.cutout()), tile, light, overlay);
            }
        }

        public static int count() {
            return BAKED.size();
        }

        /** Renders the dynamic sides of one energy cube into the cutout buffer, per the tile's render data. */
        public static void renderSides(@NotNull EnergyCubeBakedModel model, @NotNull com.mojang.blaze3d.vertex.PoseStack poseStack,
              @NotNull com.mojang.blaze3d.vertex.VertexConsumer buffer, @NotNull TileEntityEnergyCube tile, int light, int overlay) {
            TileEntityEnergyCube.CubeSideState[] states = tile.getRenderData();
            for (Direction side : Direction.values()) {
                draw(buffer, poseStack, model.getSplitQuads(side, states).unlit(), light, overlay);
                draw(buffer, poseStack, model.getSplitQuads(side, states).lit(), LightTexture.FULL_BRIGHT, overlay);
            }
            draw(buffer, poseStack, model.getSplitQuads(null, states).unlit(), light, overlay);
            draw(buffer, poseStack, model.getSplitQuads(null, states).lit(), LightTexture.FULL_BRIGHT, overlay);
        }

        private static void draw(com.mojang.blaze3d.vertex.VertexConsumer buffer, com.mojang.blaze3d.vertex.PoseStack poseStack,
              List<BakedQuad> quads, int light, int overlay) {
            for (BakedQuad quad : quads) {
                buffer.putBulkData(poseStack.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, light, overlay);
            }
        }

        static {
            //Ties this lane to the shipped JSON's cutout declaration; the BER draws on it.
            RenderType.cutout();
        }
    }
}
