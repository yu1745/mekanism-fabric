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
 * Prepares the shipped Mekanism loader models off-thread and resolves their native baked owners.
 * Official JSON remains the property authority. Transmitters have their dedicated plugin;
 * unknown loaders fail rather than falling back to empty geometry.
 */
public final class FabricMekanismModelPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricModels");
    private static final String LOADER_COMPOSITE = "neoforge:composite";
    private static final String LOADER_SEPARATE_TRANSFORMS = "neoforge:separate_transforms";
    private static final String LOADER_ITEM_LAYERS = "neoforge:item_layers";
    private static final String LOADER_OBJ = "neoforge:obj";
    /** Owned by {@code FabricTransmitterModelPlugin}; this scan only yields the id back to vanilla resolution. */
    private static final String LOADER_TRANSMITTER = "mekanism:transmitter";
    //Mekanism-specific geometry owners selected below.
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
            int energyCubeBaked = 0;
            int dataBasedBaked = 0;
            int robitBaked = 0;
            int bakedFluidContainers = 0;
            int transmitterOwned = 0;
            for (Map.Entry<ResourceLocation, Resource> entry : resources.listResources("models", id -> id.getPath().endsWith(".json")).entrySet()) {
                String json = read(entry.getValue());
                if (!entry.getKey().getNamespace().equals("mekanism")) {
                    // A datapack skin may reference a model from another resource namespace using our loader.
                    // Leave every unrelated foreign model to its own loader.
                    if (json.contains(LOADER_ROBIT)) {
                        JsonObject foreignRoot = JsonParser.parseString(json).getAsJsonObject();
                        JsonElement foreignLoader = foreignRoot.get("loader");
                        if (foreignLoader != null && foreignLoader.isJsonPrimitive() && foreignLoader.getAsJsonPrimitive().isString()
                              && LOADER_ROBIT.equals(foreignLoader.getAsString())) {
                            ResourceLocation foreignId = modelId(entry.getKey());
                            prepared.put(foreignId, new FabricRobitUnbakedModel(foreignId, foreignRoot));
                        }
                    }
                    continue;
                }
                if (!json.contains("\"loader\"")) {
                    //The energy cube tier children carry no loader - they parent onto the geometry carrier and
                    //override textures; handled with the merged map below.
                    if (entry.getKey().getPath().startsWith("models/block/energy_cube/")) {
                        JsonObject childRoot = JsonParser.parseString(json).getAsJsonObject();
                        JsonElement parent = childRoot.get("parent");
                        if (parent != null && parent.getAsString().equals("mekanism:block/energy_cube/base")) {
                            ResourceLocation id = modelId(entry.getKey());
                            prepared.put(id, new FabricEnergyCubeUnbakedModel(id, childRoot, energyCubeBaseModel(resources)));
                            energyCubeBaked++;
                        }
                    }
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
                        JsonObject base = root.getAsJsonObject("base");
                        prepared.put(id, new FabricSeparateTransformsModel(id, root, obj(id, base, resources, base.toString())));
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
                    case LOADER_ENERGY_CUBE -> {
                        //The geometry carrier: parsed here, its textures seed the tier children below.
                        prepared.put(id, new FabricEnergyCubeUnbakedModel(id, root, null));
                        energyCubeBaked++;
                    }
                    case LOADER_DATA_BASED -> {
                        //Bake both variants per block-state rotation; Fabric render data selects the chunk model.
                        prepared.put(id, new FabricDataBasedUnbakedModel(id, root, resources));
                        dataBasedBaked++;
                    }
                    case LOADER_ROBIT -> {
                        //Robit models bake as plain element models with missingno placeholders; the per-skin
                        //texture transform applies at draw time (builtin renderer for items, entity renderer for
                        //the robit itself). The item quads are suppressed by the builtin-renderer lane.
                        prepared.put(id, new FabricRobitUnbakedModel(id, root));
                        robitBaked++;
                    }
                    case LOADER_FLUID_CONTAINER -> {
                        //Bake the chemical bucket geometry while retaining the supplied parent model's texture and
                        //display properties. The fluid surface uses the still texture and tint index 1.
                        prepared.put(id, new FabricFluidContainerModel(id, root));
                        bakedFluidContainers++;
                    }
                    default -> throw new IllegalStateException("Mekanism model " + id + " declares an unhandled loader: " + loader);
                }
            }
            LOGGER.info("MEKANISM_FABRIC_MODELS_PROOF composite={} separate_transforms={} item_layers={} obj={} energy_cube_baked={} data_based_baked={} robit_baked={} fluid_container_baked={} transmitter_owned={}",
                  composites, separateTransforms, itemLayers, objModels, energyCubeBaked, dataBasedBaked,
                  robitBaked, bakedFluidContainers, transmitterOwned);
            return prepared;
        }, executor);
    }

    /** The geometry carrier, whose geometry and textures are inherited by each tier child. */
    private static JsonObject energyCubeBaseModel(ResourceManager resources) {
        ResourceLocation fileId = ResourceLocation.fromNamespaceAndPath("mekanism", "models/block/energy_cube/base.json");
        Resource resource = resources.getResource(fileId).orElseThrow();
        return JsonParser.parseString(read(resource)).getAsJsonObject();
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
