package mekanism.client.model;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Offline model-cache spine for the tile renderers. The official NeoForge class re-resolves its entries on the
 * {@code BakingCompleted} event and keeps an OBJ/geometry half for re-baking with per-user configurations (Meka-Suit
 * modules, transmitter contents); that half belongs to the armor and transmitter lanes and stays deferred there, as
 * does the robit skin cache. On Fabric the JSON half is captured by the model-cache plugin's after-bake hook
 * ({@code FabricModelCachePlugin}), which also registers the models as additional top-level models (the official
 * {@code setup(RegisterAdditional)} registration). The official {@code Mekanism.logger} error owner becomes a
 * private SLF4J logger, matching the {@code ConfigurableHeightProvider} precedent.
 */
public class BaseModelCache {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Mekanism/FabricModelCache");

    private final Map<ResourceLocation, MekanismModelData> modelMap = new Object2ObjectOpenHashMap<>();

    private final String modid;

    protected BaseModelCache(String modid) {
        this.modid = modid;
    }

    private ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(modid, path);
    }

    protected JSONModelData registerJSON(String path) {
        return registerJSON(rl(path));
    }

    protected JSONModelData registerJSON(ResourceLocation rl) {
        return register(rl, JSONModelData::new);
    }

    protected <DATA extends MekanismModelData> DATA register(ResourceLocation rl, Function<ResourceLocation, DATA> creator) {
        DATA data = creator.apply(rl);
        modelMap.put(rl, data);
        return data;
    }

    public static BakedModel getBakedModel(ResourceLocation rl) {
        BakedModel bakedModel = ((FabricBakedModelManager) Minecraft.getInstance().getModelManager()).getModel(rl);
        if (bakedModel == null) {
            LOGGER.error("Baked model doesn't exist: {}", rl);
            return Minecraft.getInstance().getModelManager().getMissingModel();
        }
        return bakedModel;
    }

    public Map<ResourceLocation, MekanismModelData> models() {
        return modelMap;
    }

    String modid() {
        return modid;
    }

    public static class MekanismModelData {

        protected final ResourceLocation rl;

        protected MekanismModelData(ResourceLocation rl) {
            this.rl = rl;
        }

        /**
         * The model ids the Fabric plugin must register as additional top-level models, mirroring the official
         * {@code setup(RegisterAdditional)} registration.
         */
        public void acceptBakedModel(@Nullable BakedModel bakedModel) {
        }

        public void collectAdditionalModels(List<ResourceLocation> models) {
        }

        public ResourceLocation modelId() {
            return rl;
        }
    }

    public static class JSONModelData extends MekanismModelData {

        private BakedModel bakedModel;

        private JSONModelData(ResourceLocation rl) {
            super(rl);
        }

        @Override
        public void acceptBakedModel(@Nullable BakedModel bakedModel) {
            this.bakedModel = bakedModel;
        }

        @Override
        public void collectAdditionalModels(List<ResourceLocation> models) {
            models.add(rl);
        }

        public List<BakedQuad> getQuads(RandomSource random) {
            //TODO: Decide if this should just redirect to the other get quads method (some impls might be different depending on if it gets data and render type vs not)
            return getBakedModel().getQuads(null, null, random);
        }

        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return getBakedModel().getQuads(state, side, rand);
        }

        public BakedModel getBakedModel() {
            return bakedModel;
        }
    }
}
