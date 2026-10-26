package mekanism.fabric.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import mekanism.fabric.client.render.obj.FabricObjGeometryReader;
import mekanism.fabric.client.render.obj.TransmitterObjGeometry;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.client.renderer.block.model.BlockModel;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric model-loading closure for the shipped Mekanism models that declare a NeoForge loader key, which vanilla's
 * model pipeline cannot parse.
 *
 * <p>The official files stay packaged unchanged and remain the source of truth; this plugin parses them in the
 * off-thread preparable phase and resolves each through a Mekanism-owned {@code UnbakedModel} with matching baking
 * semantics:</p>
 *
 * <ul>
 * <li>{@code neoforge:composite} - baked as the merge of its declared inline children (factory machines).</li>
 * <li>{@code neoforge:separate_transforms} - baked as its inline {@code neoforge:obj} base; the perspective swap to
 * a mirrored Meka-Tool OBJ is a documented visual delta (vanilla 1.21.1 offers no per-perspective model hook on
 * Fabric).</li>
 * <li>{@code neoforge:item_layers} - rebased onto the vanilla {@code minecraft:item/generated} layer stack; the
 * NeoForge per-layer full-brightness of the QIO dashboard LED is a documented visual delta.</li>
 * </ul>
 *
 * <p>Loud deferrals (each counted in the proof line, never faked): {@code mekanism:energy_cube},
 * {@code mekanism:data_based} and {@code mekanism:robit} fall back to a vanilla plain parse (tier models parent
 * onto them, so a resolver intervention would break the BlockModel parent cast); the {@code neoforge:fluid_container}
 * family resolves through {@link FabricDeferredLoaderModel} because its {@code neoforge:item/bucket} parent exists
 * only in the NeoForge jar; {@code mekanism:transmitter} belongs to the dedicated transmitter plugin. Unknown
 * loaders still fail loudly, so a future loader can never be dropped silently.</p>
 */
public final class FabricMekanismModelPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricModels");
    private static final String LOADER_COMPOSITE = "neoforge:composite";
    private static final String LOADER_SEPARATE_TRANSFORMS = "neoforge:separate_transforms";
    private static final String LOADER_ITEM_LAYERS = "neoforge:item_layers";
    private static final String LOADER_OBJ = "neoforge:obj";
    /** Owned by {@code FabricTransmitterModelPlugin}; this scan only yields the id back to vanilla resolution. */
    private static final String LOADER_TRANSMITTER = "mekanism:transmitter";
    /**
     * Deliberate deferrals, each loud and counted in the proof line: their official baked forms read NeoForge model
     * data or NeoForge-owned base assets, which have no Fabric counterpart yet. Vanilla then parses these files as
     * plain element-less models, so resolution stays safe; faking the dynamic half here would produce wrong visuals
     * instead of an honest gap.
     */
    private static final String LOADER_ENERGY_CUBE = "mekanism:energy_cube";
    private static final String LOADER_DATA_BASED = "mekanism:data_based";
    private static final String LOADER_ROBIT = "mekanism:robit";
    private static final String LOADER_FLUID_CONTAINER = "neoforge:fluid_container";

    private FabricMekanismModelPlugin() {
    }

    /** Registers the preparable loader; call once from the client bootstrap before the first resource reload. */
    public static void initialize() {
        PreparableModelLoadingPlugin.register(FabricMekanismModelPlugin::prepare, (prepared, context) ->
              context.resolveModel().register(resolver -> prepared.get(resolver.id())));
    }

    /** Off-thread scan of every shipped mekanism model JSON for loader declarations. */
    private static CompletableFuture<Map<ResourceLocation, net.minecraft.client.resources.model.UnbakedModel>> prepare(
          ResourceManager resources, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            Map<ResourceLocation, net.minecraft.client.resources.model.UnbakedModel> prepared = new LinkedHashMap<>();
            int composites = 0;
            int separateTransforms = 0;
            int itemLayers = 0;
            int objModels = 0;
            int deferredEnergyCubes = 0;
            int deferredDataBased = 0;
            int deferredRobit = 0;
            int deferredFluidContainers = 0;
            int transmitterOwned = 0;
            for (Map.Entry<ResourceLocation, Resource> entry : resources.listResources("models", id -> id.getPath().endsWith(".json")).entrySet()) {
                if (!entry.getKey().getNamespace().equals("mekanism")) {
                    continue;
                }
                String json = read(entry.getValue());
                if (!json.contains("\"loader\"")) {
                    continue;
                }
                ResourceLocation id = modelId(entry.getKey());
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                String loader = loaderOf(id, root);
                switch (loader) {
                    case LOADER_COMPOSITE -> {
                        prepared.put(id, composite(id, root));
                        composites++;
                    }
                    case LOADER_SEPARATE_TRANSFORMS -> {
                        //Only the base model is baked: vanilla 1.21.1 on Fabric has no per-perspective model hook,
                        //so the mirrored left-hand Meka-Tool OBJ stays an explicitly documented visual delta. The
                        //outer JSON stays the property authority (parent chain, transforms, particle).
                        LOGGER.debug("separate_transforms model {} bakes its base OBJ for every perspective", id);
                        prepared.put(id, obj(id, root.getAsJsonObject("base"), resources, root.toString()));
                        separateTransforms++;
                    }
                    case LOADER_ITEM_LAYERS -> {
                        prepared.put(id, new FabricItemLayersModel(id, root));
                        itemLayers++;
                    }
                    case LOADER_OBJ -> {
                        prepared.put(id, obj(id, root, resources, root.toString()));
                        objModels++;
                    }
                    case LOADER_TRANSMITTER -> transmitterOwned++;
                    case LOADER_ENERGY_CUBE -> deferredEnergyCubes++;
                    case LOADER_DATA_BASED -> deferredDataBased++;
                    case LOADER_ROBIT -> deferredRobit++;
                    case LOADER_FLUID_CONTAINER -> {
                        //The only deferred family whose vanilla parse crashes the reload: its parent
                        //neoforge:item/bucket exists solely in the NeoForge jar. The explicit deferred model cuts
                        //that chain and bakes the vanilla missing marker; the others stay vanilla-parsed because
                        //tier models parent onto them and would trip the BlockModel parent cast otherwise.
                        prepared.put(id, FabricDeferredLoaderModel.INSTANCE);
                        deferredFluidContainers++;
                    }
                    default -> throw new IllegalStateException("Mekanism model " + id + " declares an unhandled loader: " + loader);
                }
            }
            LOGGER.info("MEKANISM_FABRIC_MODELS_PROOF composite={} separate_transforms={} item_layers={} obj={} energy_cube_deferred={} data_based_deferred={} robit_deferred={} fluid_container_deferred={} transmitter_owned={}",
                  composites, separateTransforms, itemLayers, objModels, deferredEnergyCubes, deferredDataBased,
                  deferredRobit, deferredFluidContainers, transmitterOwned);
            return prepared;
        }, executor);
    }

    private static String loaderOf(ResourceLocation id, JsonObject root) {
        JsonElement loader = root.get("loader");
        if (loader == null || !loader.isJsonPrimitive() || !loader.getAsJsonPrimitive().isString()) {
            throw new IllegalStateException("Mekanism model " + id + " declares a non-string loader: " + loader);
        }
        return loader.getAsString();
    }

    private static FabricCompositeModel composite(ResourceLocation id, JsonObject root) {
        JsonElement children = root.get("children");
        if (children == null || !children.isJsonObject() || children.getAsJsonObject().entrySet().isEmpty()) {
            throw new IllegalStateException("Composite model declares no children: " + id);
        }
        List<Pair<String, BlockModel>> parsed = new ArrayList<>();
        for (Map.Entry<String, JsonElement> child : children.getAsJsonObject().entrySet()) {
            if (!child.getValue().isJsonObject()) {
                throw new IllegalStateException("Composite child '" + child.getKey() + "' is not an object: " + id);
            }
            parsed.add(Pair.of(child.getKey(), BlockModel.fromString(child.getValue().toString())));
        }
        //The outer JSON (parent chain for transforms, particle texture) seeds the BlockModel superclass; its unknown
        //"loader"/"children" keys are ignored by the vanilla deserializer.
        return new FabricCompositeModel(id, root.toString(), parsed);
    }

    private static FabricObjModel obj(ResourceLocation id, JsonObject def, ResourceManager resources, String propertyJson) {
        if (!LOADER_OBJ.equals(loaderOf(id, def))) {
            throw new IllegalStateException("Mekanism obj model must declare " + LOADER_OBJ + ": " + id);
        }
        TransmitterObjGeometry geometry = FabricObjGeometryReader.read(resources, id, def);
        return FabricObjModel.of(id, propertyJson, geometry);
    }

    /** The model id, i.e. the resource path below {@code models/} without the {@code .json} suffix. */
    static ResourceLocation modelId(ResourceLocation filePath) {
        String path = filePath.getPath();
        return ResourceLocation.fromNamespaceAndPath(filePath.getNamespace(),
              path.substring("models/".length(), path.length() - ".json".length()));
    }

    private static String read(Resource resource) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line).append('\n');
            }
            return json.toString();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
