package mekanism.fabric.client.model;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric registry of the baked data-based model halves: the static bake keeps the no-data (unlit) model, and the
 * property-based (lit) models land here keyed by model id for the tile renderers that replaced the NeoForge
 * model-data switch.
 */
public final class FabricDataBasedModels {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricDataBasedModels");
    private static final Map<ResourceLocation, BakedModel> LIT_MODELS = new HashMap<>();
    private static final AtomicBoolean PROOF = new AtomicBoolean();

    private FabricDataBasedModels() {
    }

    public static void registerLit(ResourceLocation modelId, BakedModel litModel) {
        LIT_MODELS.put(modelId, litModel);
        if (PROOF.compareAndSet(false, true)) {
            LOGGER.info("MEKANISM_FABRIC_DATA_BASED_PROOF baked={}", LIT_MODELS.size());
        }
    }

    /** The lit (property-based) model for a model id, or null when that model has no data-based half. */
    public static BakedModel getLitModel(ResourceLocation modelId) {
        return LIT_MODELS.get(modelId);
    }
}
