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

    @Override
    public void onInitializeModelLoader(Context context) {
        List<JSONModelData> jsonModels = new ArrayList<>();
        Map<ResourceLocation, JSONModelData> byId = new HashMap<>();
        for (MekanismModelData data : MekanismModelCache.INSTANCE.models().values()) {
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
        //The OBJ half of the cache re-parses lazily on next use; clearing here mirrors the official BakingCompleted
        //reload (the model-cache class documents the seam).
        mekanism.client.model.MekanismModelCache.INSTANCE.onReload();
        context.modifyModelAfterBake().register((model, bakeContext) -> {
            JSONModelData json = byId.get(bakeContext.resourceId());
            if (json != null) {
                json.acceptBakedModel(model);
            }
            return model;
        });
        LOGGER.info("MEKANISM_FABRIC_MODEL_CACHE_PROOF json_models={}", jsonModels.size());
    }
}
