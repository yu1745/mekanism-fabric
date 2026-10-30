package mekanism.fabric.client.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mekanism.client.model.BaseModelCache.MekanismModelData;
import mekanism.client.model.BaseModelCache.JSONModelData;
import mekanism.client.model.MekanismModelCache;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers the tile renderers' offline model-cache entries as additional top-level models (the official
 * {@code setup(RegisterAdditional)} registration) and captures their baked models at the end of every bake pass
 * (the official {@code BakingCompleted} handler). Entry capture is keyed by the plain resource id the plugin adds,
 * so a cache entry can never silently miss its bake.
 */
public final class FabricModelCachePlugin implements ModelLoadingPlugin {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Mekanism/FabricModelCache");

    private static final List<mekanism.client.model.BaseModelCache> MODULE_CACHES = new ArrayList<>();

    public static void registerModuleCache(mekanism.client.model.BaseModelCache cache) {
        MODULE_CACHES.add(cache);
    }

    @Override
    public void onInitializeModelLoader(Context context) {
        List<JSONModelData> jsonModels = new ArrayList<>();
        Map<ResourceLocation, JSONModelData> byId = new HashMap<>();
        for (MekanismModelData data : java.util.stream.Stream.concat(java.util.stream.Stream.of(MekanismModelCache.INSTANCE), MODULE_CACHES.stream())
              .flatMap(cache -> cache.models().values().stream()).toList()) {
            List<ResourceLocation> models = new ArrayList<>();
            data.collectAdditionalModels(models);
            for (ResourceLocation id : models) {
                if (data instanceof JSONModelData json) {
                    jsonModels.add(json);
                    byId.put(id, json);
                }
            }
        }
        context.addModels(byId.keySet());
        MekanismModelCache.INSTANCE.clearRobitModels();
        context.addModels(MekanismModelCache.BASE_ROBIT_MODEL);
        //The OBJ half of the cache re-parses lazily on next use; clearing here mirrors the official BakingCompleted
        //reload (the model-cache class documents the seam).
        mekanism.client.model.MekanismModelCache.INSTANCE.onReload();
        //Capture after Mekanism's model wrappers so cached drive quads retain their recovered light metadata.
        context.modifyModelAfterBake().register(net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier.WRAP_LAST_PHASE, (model, bakeContext) -> {
            JSONModelData json = byId.get(bakeContext.resourceId());
            if (json != null) {
                json.acceptBakedModel(model);
            }
            if (model != null && MekanismModelCache.BASE_ROBIT_MODEL.equals(bakeContext.resourceId())) {
                MekanismModelCache.INSTANCE.setRobitModelBaker(model, bakeContext.baker());
                LOGGER.info("MEKANISM_FABRIC_ROBIT_MODEL_PROOF base_quads={}", model.getQuads(null, null, net.minecraft.util.RandomSource.create()).size());
            }
            //The official addCustomModel wrappers: the miner's holiday texture transform rides the keyed cache.
            if (model != null && net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mekanism", "block/digital_miner")
                  .equals(bakeContext.resourceId())) {
                return new mekanism.client.model.baked.DigitalMinerBakedModel(model);
            }
            return model;
        });
        LOGGER.info("MEKANISM_FABRIC_MODEL_CACHE_PROOF json_models={}", jsonModels.size());
    }
}
