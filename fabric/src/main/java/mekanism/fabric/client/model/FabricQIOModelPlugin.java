package mekanism.fabric.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import mekanism.common.registries.MekanismBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Preserves QIO drive geometry and the official per-face/per-layer light data through the native renderer. */
public final class FabricQIOModelPlugin {
    private static final ResourceLocation SHELL = id("block/qio_drive_array");
    private static final ResourceLocation ITEM = id("item/qio_drive_array");
    private static final ResourceLocation DASHBOARD = id("item/portable_qio_dashboard");
    private static final List<ResourceLocation> DRIVES = List.of(id("block/qio_drive/qio_drive_offline"),
          id("block/qio_drive/qio_drive_empty"), id("block/qio_drive/qio_drive_partial"), id("block/qio_drive/qio_drive_full"));

    private FabricQIOModelPlugin() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", path);
    }

    public static void initialize() {
        PreparableModelLoadingPlugin.register(FabricQIOModelPlugin::prepare, (models, context) ->
              context.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, bake) -> {
                  ResourceLocation id = bake.resourceId();
                  // Vanilla bakes inventory models as top-level variants rather than by their item/ resource id.
                  if (net.minecraft.client.resources.model.ModelResourceLocation.inventory(id("qio_drive_array")).equals(bake.topLevelId())) {
                      id = ITEM;
                  } else if (net.minecraft.client.resources.model.ModelResourceLocation.inventory(id("portable_qio_dashboard")).equals(bake.topLevelId())) {
                      id = DASHBOARD;
                  }
                  if (model == null || id == null || !models.containsKey(id)) {
                      return model;
                  }
                  JsonObject geometry = models.get(id);
                  // The shipped item inherits the shell geometry. A resource pack can instead supply its own elements.
                  if (id.equals(ITEM) && !geometry.has("elements") && geometry.has("parent") && SHELL.toString().equals(geometry.get("parent").getAsString())) {
                      geometry = models.get(SHELL);
                  }
                  List<LitQuad> light = bake.sourceModel() instanceof BlockModel blockModel
                        ? bakedLight(geometry, blockModel, bake.textureGetter(), bake.settings()) : List.of();
                  if (id.equals(ITEM) || DRIVES.contains(id)) {
                      org.slf4j.LoggerFactory.getLogger("Mekanism/FabricQIOModels").info(
                            "MEKANISM_FABRIC_QIO_MODEL_PROOF model={} item_layer={} light_faces={}", id, id.equals(ITEM), light.size());
                  }
                  return new QIOModel(model, light, id.equals(ITEM), layerLight(geometry));
              }));
    }

    private static CompletableFuture<Map<ResourceLocation, JsonObject>> prepare(ResourceManager resources, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            Map<ResourceLocation, JsonObject> models = new HashMap<>();
            List<ResourceLocation> ids = new ArrayList<>(DRIVES);
            ids.add(SHELL);
            ids.add(ITEM);
            ids.add(DASHBOARD);
            for (ResourceLocation id : ids) {
                ResourceLocation file = id.withPath(path -> "models/" + path + ".json");
                resources.getResource(file).ifPresent(resource -> {
                    try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                        models.put(id, JsonParser.parseReader(reader).getAsJsonObject());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
            return models;
        }, executor);
    }

    static List<LitQuad> bakedLight(JsonObject json, BlockModel model, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        if (json == null || !json.has("elements")) {
            return List.of();
        }
        JsonArray raw = json.getAsJsonArray("elements");
        List<BlockElement> elements = model.getElements();
        if (raw.size() != elements.size()) {
            return List.of();
        }
        List<LitQuad> result = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            JsonObject elementJson = raw.get(i).getAsJsonObject();
            JsonObject data = elementJson.has("neoforge_data") ? elementJson.getAsJsonObject("neoforge_data") : null;
            int block = data == null ? 0 : GsonHelper.getAsInt(data, "block_light", 0);
            int sky = data == null ? 0 : GsonHelper.getAsInt(data, "sky_light", 0);
            if (block == 0 && sky == 0) {
                continue;
            }
            BlockElement element = elements.get(i);
            element.faces.forEach((direction, face) -> {
                BakedQuad quad = BlockModel.bakeFace(element, face, sprites.apply(model.getMaterial(face.texture())), direction, state);
                result.add(new LitQuad(quad.getVertices(), (block << 4) | (sky << 20)));
            });
        }
        return List.copyOf(result);
    }

    record LitQuad(int[] vertices, int light) {
        boolean matches(BakedQuad quad) {
            return Arrays.equals(vertices, quad.getVertices());
        }
    }

    private static Map<Integer, Integer> layerLight(JsonObject model) {
        JsonObject data = model.getAsJsonObject("neoforge_data");
        if (data == null || !data.has("layers")) {
            return Map.of();
        }
        Map<Integer, Integer> light = new HashMap<>();
        data.getAsJsonObject("layers").entrySet().forEach(entry -> {
            JsonObject layer = entry.getValue().getAsJsonObject();
            light.put(Integer.parseInt(entry.getKey()), LightTexture.pack(
                  GsonHelper.getAsInt(layer, "block_light", 0), GsonHelper.getAsInt(layer, "sky_light", 0)));
        });
        return Map.copyOf(light);
    }

    public static int lightOf(BakedModel model, BakedQuad quad) {
        return model instanceof QIOModel qio ? qio.light(quad) : 0;
    }

    private record QIOModel(BakedModel original, List<LitQuad> lit, boolean item, Map<Integer, Integer> layerLight) implements BakedModel, FabricBakedModel {
        private int light(BakedQuad quad) {
            if (layerLight.containsKey(quad.getTintIndex())) {
                return layerLight.get(quad.getTintIndex());
            }
            for (LitQuad candidate : lit) {
                if (candidate.matches(quad)) {
                    return candidate.light();
                }
            }
            return 0;
        }

        @Override public boolean isVanillaAdapter() { return false; }

        @Override
        public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
            FabricBakedModel fabric = (FabricBakedModel) original;
            if (!fabric.isVanillaAdapter()) {
                fabric.emitBlockQuads(view, state, pos, random, context);
            } else {
                emitBase(state, random, context, true);
            }
        }

        @Override
        public void emitItemQuads(ItemStack stack, Supplier<RandomSource> random, RenderContext context) {
            FabricBakedModel fabric = (FabricBakedModel) original;
            if (!fabric.isVanillaAdapter()) {
                fabric.emitItemQuads(stack, random, context);
            } else {
                emitBase(null, random, context, false);
            }
            if (item && stack.is(MekanismBlocks.QIO_DRIVE_ARRAY.getItemHolder())) {
                FabricQIODriveGeometry.forEach(FabricQIODriveGeometry.itemStatus(stack), MekanismBlocks.QIO_DRIVE_ARRAY.defaultState(), random.get(),
                      (quad, light) -> emitQuad(context, quad, null, light));
            }
        }

        private void emitBase(@Nullable BlockState state, Supplier<RandomSource> random, RenderContext context, boolean cull) {
            for (Direction side : Direction.values()) {
                for (BakedQuad quad : original.getQuads(state, side, random.get())) {
                    emitQuad(context, quad, cull ? side : null, light(quad));
                }
            }
            for (BakedQuad quad : original.getQuads(state, null, random.get())) {
                emitQuad(context, quad, null, light(quad));
            }
        }

        private static void emitQuad(RenderContext context, BakedQuad quad, @Nullable Direction cull, int light) {
            RenderMaterial material = RendererAccess.INSTANCE.getRenderer().materialFinder().emissive(light == LightTexture.FULL_BRIGHT).find();
            var emitter = context.getEmitter();
            emitter.fromVanilla(quad, material, cull);
            if (light != 0 && light != LightTexture.FULL_BRIGHT) {
                emitter.lightmap(light, light, light, light);
            }
            emitter.emit();
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) { return original.getQuads(state, side, random); }
        @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return original.isGui3d(); }
        @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return original.isCustomRenderer(); }
        @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
        @Override public ItemTransforms getTransforms() { return original.getTransforms(); }
        @Override public ItemOverrides getOverrides() {
            if (!item) {
                return original.getOverrides();
            }
            return new ItemOverrides(null, null, List.of()) {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                    BakedModel resolved = original.getOverrides().resolve(original, stack, level, entity, seed);
                    if (resolved == null || resolved == original) {
                        return model;
                    }
                    return new QIOModel(resolved, List.of(), true, Map.of());
                }
            };
        }
    }
}
