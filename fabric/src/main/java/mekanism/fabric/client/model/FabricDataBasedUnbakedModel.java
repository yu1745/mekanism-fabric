package mekanism.fabric.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The shipped data-based model selects a complete baked model from the block entity's render data. */
final class FabricDataBasedUnbakedModel implements UnbakedModel {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Mekanism/FabricDataBasedModels");
    private static final java.util.concurrent.atomic.AtomicBoolean PROOF = new java.util.concurrent.atomic.AtomicBoolean();
    private final ResourceLocation id;
    private final JsonObject root;
    private final ResourceManager resources;
    private UnbakedModel noData;
    private UnbakedModel emitting;
    private ResourceLocation emittingId;

    FabricDataBasedUnbakedModel(ResourceLocation id, JsonObject root, ResourceManager resources) {
        this.id = id;
        this.root = root;
        this.resources = resources;
    }

    @Override
    public @NotNull Collection<ResourceLocation> getDependencies() {
        return List.of(noDataId(), emittingId());
    }

    @Override
    public void resolveParents(@NotNull Function<ResourceLocation, UnbakedModel> resolver) {
        noData = resolve(resolver, noDataId());
        emitting = resolve(resolver, emittingId());
    }

    @Override
    public @NotNull BakedModel bake(@NotNull ModelBaker baker, @NotNull Function<Material, TextureAtlasSprite> spriteGetter, @NotNull ModelState modelState) {
        BakedModel noDataBaked = noData.bake(baker, spriteGetter, modelState);
        BakedModel emittingBaked = emitting.bake(baker, spriteGetter, modelState);
        List<LitQuad> noDataLight = bakedLight(noDataId(), noData, spriteGetter, modelState);
        List<LitQuad> emittingLight = bakedLight(emittingId(), emitting, spriteGetter, modelState);
        if (PROOF.compareAndSet(false, true)) {
            LOGGER.info("MEKANISM_FABRIC_DATA_BASED_PROOF baked=1");
        }
        return new DataBasedModel(noDataBaked, emittingBaked, noDataLight, emittingLight);
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

    private ResourceLocation emittingId() {
        if (emittingId == null) {
            String propertyKey = root.keySet().stream().filter(k -> !k.equals("no_data") && !k.equals("loader")).findFirst().orElseThrow();
            emittingId = readPath(propertyKey);
        }
        return emittingId;
    }

    private ResourceLocation readPath(String key) {
        return ResourceLocation.parse(GsonHelper.getAsString(root, key));
    }

    /** Recover light metadata vanilla's BlockElement parser ignores, without changing the shipped model JSON. */
    private List<LitQuad> bakedLight(ResourceLocation modelId, UnbakedModel model, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        if (!(model instanceof BlockModel blockModel)) {
            return List.of();
        }
        ResourceLocation file = modelId.withPath(path -> "models/" + path + ".json");
        var resource = resources.getResource(file).orElseThrow();
        JsonObject json;
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!json.has("elements")) {
            return List.of();
        }
        JsonArray rawElements = json.getAsJsonArray("elements");
        List<BlockElement> elements = blockModel.getElements();
        if (rawElements.size() != elements.size()) {
            throw new IllegalStateException("Element mismatch in data-based model " + modelId);
        }
        List<LitQuad> result = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            JsonObject raw = rawElements.get(i).getAsJsonObject();
            JsonObject metadata = raw.has("neoforge_data") ? raw.getAsJsonObject("neoforge_data") : null;
            int block = metadata == null ? 0 : GsonHelper.getAsInt(metadata, "block_light", 0);
            int sky = metadata == null ? 0 : GsonHelper.getAsInt(metadata, "sky_light", 0);
            if (block == 0 && sky == 0) {
                continue;
            }
            BlockElement element = elements.get(i);
            for (var face : element.faces.entrySet()) {
                BlockElementFace value = face.getValue();
                BakedQuad quad = BlockModel.bakeFace(element, value, sprites.apply(blockModel.getMaterial(value.texture())), face.getKey(), state);
                result.add(new LitQuad(quad.getVertices(), (block << 4) | (sky << 20)));
            }
        }
        return result;
    }

    private record LitQuad(int[] vertices, int light) {
        boolean matches(BakedQuad quad) {
            return java.util.Arrays.equals(vertices, quad.getVertices());
        }
    }

    /** One instance per state bake, so both variants retain the exact state rotation. */
    record DataBasedModel(BakedModel noData, BakedModel emitting, List<LitQuad> noDataLight, List<LitQuad> emittingLight)
          implements BakedModel, FabricBakedModel {

        @Override public boolean isVanillaAdapter() { return false; }

        @Override
        public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
            Object data = view instanceof FabricBlockView fabricView ? fabricView.getBlockEntityRenderData(pos) : null;
            boolean on = Boolean.TRUE.equals(data);
            emit(on ? emitting : noData, on ? emittingLight : noDataLight, state, random, context, true);
        }

        @Override
        public void emitItemQuads(ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
            emit(noData, noDataLight, null, random, context, false);
        }

        private static void emit(BakedModel model, List<LitQuad> light, @Nullable BlockState state, Supplier<RandomSource> random,
              RenderContext context, boolean cull) {
            var finder = RendererAccess.INSTANCE.getRenderer().materialFinder().blendMode(BlendMode.SOLID);
            RenderMaterial normal = finder.emissive(false).find();
            RenderMaterial fullbright = finder.emissive(true).find();
            for (Direction direction : Direction.values()) {
                emitFaces(model.getQuads(state, direction, random.get()), light, context, normal, fullbright, cull ? direction : null);
            }
            emitFaces(model.getQuads(state, null, random.get()), light, context, normal, fullbright, null);
        }

        private static void emitFaces(List<BakedQuad> quads, List<LitQuad> light, RenderContext context, RenderMaterial normal,
              RenderMaterial fullbright, @Nullable Direction cull) {
            var emitter = context.getEmitter();
            for (BakedQuad quad : quads) {
                int bakedLight = 0;
                for (LitQuad candidate : light) {
                    if (candidate.matches(quad)) {
                        bakedLight = candidate.light();
                        break;
                    }
                }
                emitter.fromVanilla(quad, bakedLight == 0xF000F0 ? fullbright : normal, cull);
                if (bakedLight != 0 && bakedLight != 0xF000F0) {
                    emitter.lightmap(bakedLight, bakedLight, bakedLight, bakedLight);
                }
                emitter.emit();
            }
        }

        @Override public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random) {
            return noData.getQuads(state, side, random);
        }
        @Override public boolean useAmbientOcclusion() { return noData.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return noData.isGui3d(); }
        @Override public boolean usesBlockLight() { return noData.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return noData.isCustomRenderer(); }
        @Override public @NotNull TextureAtlasSprite getParticleIcon() { return noData.getParticleIcon(); }
        @Override public @NotNull ItemOverrides getOverrides() { return noData.getOverrides(); }
        @Override public @NotNull ItemTransforms getTransforms() { return noData.getTransforms(); }
    }
}
