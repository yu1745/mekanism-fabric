package mekanism.fabric.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import mekanism.client.model.energycube.EnergyCubeBakedModel;
import mekanism.client.model.energycube.EnergyCubeGeometry;
import mekanism.client.model.energycube.EnergyCubeModelLoader;
import mekanism.common.tile.TileEntityEnergyCube;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric closure for the official {@code mekanism:energy_cube} model family. The shipped geometry carrier
 * ({@code block/energy_cube/base.json}) declares the loader; the five tier models parent onto it and override only the
 * textures. The plugin parses each tier's merged texture map through the reshaped official loader/geometry, then:
 *
 * <ul>
 * <li>Fabric chunk meshing reads the block entity's side-data snapshot and emits the frame and LEDs/ports;</li>
 * <li>the item renderer uses the same geometry split with the item's side configuration.</li>
 * </ul>
 */
public final class FabricEnergyCubeModel implements BakedModel, FabricBakedModel {

    private static final ResourceLocation ATLAS = ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    private final EnergyCubeBakedModel dynamic;
    private final TextureAtlasSprite particle;

    private FabricEnergyCubeModel(EnergyCubeBakedModel dynamic, TextureAtlasSprite particle) {
        this.dynamic = dynamic;
        this.particle = particle;
    }

    public EnergyCubeBakedModel dynamic() {
        return dynamic;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
        Object data = view instanceof FabricBlockView fabricView ? fabricView.getBlockEntityRenderData(pos) : null;
        TileEntityEnergyCube.CubeSideState[] states = data instanceof TileEntityEnergyCube.CubeSideState[] sideStates ? sideStates : null;
        emitGeometry(context, states, true);
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
        emitGeometry(context, null, false);
    }

    private void emitGeometry(RenderContext context, @Nullable TileEntityEnergyCube.CubeSideState[] states, boolean cull) {
        var finder = RendererAccess.INSTANCE.getRenderer().materialFinder().blendMode(BlendMode.CUTOUT);
        RenderMaterial unlit = finder.emissive(false).find();
        RenderMaterial lit = finder.emissive(true).find();
        for (Direction side : Direction.values()) {
            emit(context, dynamic.getFrameQuads(side), unlit, cull ? side : null);
            EnergyCubeBakedModel.QuadSplits split = dynamic.getSplitQuads(side, states);
            emit(context, split.unlit(), unlit, cull ? side : null);
            emit(context, split.lit(), lit, cull ? side : null);
        }
        emit(context, dynamic.getFrameQuads(null), unlit, null);
        EnergyCubeBakedModel.QuadSplits split = dynamic.getSplitQuads(null, states);
        emit(context, split.unlit(), unlit, null);
        emit(context, split.lit(), lit, null);
    }

    private static void emit(RenderContext context, List<BakedQuad> quads, RenderMaterial material, @Nullable Direction cullFace) {
        var emitter = context.getEmitter();
        for (BakedQuad quad : quads) {
            emitter.fromVanilla(quad, material, cullFace);
            emitter.emit();
        }
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        return dynamic.getFrameQuads(side);
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
     * texture map and bakes it once sprites are stitched, using the current block-state rotation.
     */
    public static FabricEnergyCubeModel bake(ResourceLocation id, JsonObject root, @Nullable JsonObject parentRoot,
          Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState) {
        EnergyCubeGeometry geometry = EnergyCubeModelLoader.INSTANCE.read(parentRoot == null ? root : parentRoot);
        Map<String, String> textures = new HashMap<>();
        if (parentRoot != null && parentRoot.has("textures")) {
            for (Map.Entry<String, JsonElement> entry : parentRoot.getAsJsonObject("textures").entrySet()) {
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

        EnergyCubeBakedModel dynamic = geometry.bake(rawSpriteGetter, modelState, particle);
        return new FabricEnergyCubeModel(dynamic, particle);
    }

    /** Renders the side geometry belonging to the exact baked block-state model. */
    public static final class FabricEnergyCubeModels {
        private FabricEnergyCubeModels() {
        }

        public static void renderSides(@NotNull EnergyCubeBakedModel model, @NotNull com.mojang.blaze3d.vertex.PoseStack poseStack,
              @NotNull com.mojang.blaze3d.vertex.VertexConsumer buffer, @Nullable TileEntityEnergyCube.CubeSideState[] states, int light, int overlay) {
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
    }
}
