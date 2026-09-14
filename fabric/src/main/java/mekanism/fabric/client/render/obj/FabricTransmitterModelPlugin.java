package mekanism.fabric.client.render.obj;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import mekanism.fabric.client.render.obj.FabricTransmitterAssets.Assets;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real Fabric model-loading plugin for the shipped {@code "loader": "mekanism:transmitter"} models.
 *
 * <p>Assets are read off-thread by the {@link PreparableModelLoadingPlugin} phase; a
 * {@link ModelLoadingPlugin.Context#resolveModel()} listener then returns a real {@link UnbakedModel} for every
 * transmitter base and every model that inherits from one. The transmitter base declares the loader but the tier models
 * carry the actual texture aliases, so the same geometry assets are attached to each descendant and the vanilla
 * {@link BlockModel} delegate supplies that descendant's own alias map through the real parent chain.</p>
 *
 * <p>Every resource reload creates a fresh plugin context and a fresh preloaded map, so each reload rebuilds the
 * sprite-bearing baked models against the reloaded sprites instead of reusing a stale sprite cache.</p>
 */
public final class FabricTransmitterModelPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricTransmitterModels");

    /** The transmitter loader id declared by the shipped model JSONs. */
    public static final String LOADER_ID = "mekanism:transmitter";

    private FabricTransmitterModelPlugin() {
    }

    /** Registers the preparable loader; call once from the client bootstrap. */
    public static void initialize() {
        PreparableModelLoadingPlugin.register(FabricTransmitterModelPlugin::prepare, (prepared, context) ->
              context.resolveModel().register(resolver -> {
                  Prepared model = prepared.get(resolver.id());
                  if (model == null) {
                      return null;
                  }
                  return new FabricTransmitterBlockModel(resolver.id().toString(), model.json(), model.assets().obj(),
                        model.assets().glass(), model.assets().resources(), TransmitterModelConfig::opaqueTransmitters);
              }));
    }

    /** Off-thread discovery plus asset read of every transmitter model in the pack. */
    private static CompletableFuture<Map<ResourceLocation, Prepared>> prepare(ResourceManager resources, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            Map<ResourceLocation, ResourceLocation> discovered = FabricTransmitterModels.discover(resources);
            Map<ResourceLocation, Prepared> result = new LinkedHashMap<>();
            Map<ResourceLocation, Assets> byBase = new LinkedHashMap<>();
            int bases = 0;
            for (Map.Entry<ResourceLocation, ResourceLocation> entry : discovered.entrySet()) {
                ResourceLocation modelId = entry.getKey();
                ResourceLocation baseId = entry.getValue();
                String json = FabricTransmitterAssets.tryReadJsonText(resources, modelId);
                if (json == null) {
                    throw new IllegalStateException("Discovered transmitter model has no JSON: " + modelId);
                }
                Assets assets = byBase.get(baseId);
                if (assets == null) {
                    try {
                        assets = FabricTransmitterAssets.read(resources, baseId);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to read transmitter base assets for " + baseId, e);
                    }
                    byBase.put(baseId, assets);
                    bases++;
                }
                result.put(modelId, new Prepared(json, assets));
            }
            LOGGER.info("MEKANISM_FABRIC_TRANSMITTER_MODELS models={} bases={} loader={}", result.size(), bases, LOADER_ID);
            return Map.copyOf(result);
        }, executor);
    }

    private record Prepared(String json, Assets assets) {
    }
}
