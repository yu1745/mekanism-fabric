package mekanism.client.model;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import mekanism.fabric.client.render.obj.TransmitterObjGeometry;
import mekanism.fabric.client.render.obj.TransmitterQuadBaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.server.packs.resources.ResourceManager;
import java.util.Set;
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

    protected final Map<ResourceLocation, MekanismModelData> modelMap = new Object2ObjectOpenHashMap<>();

    protected final String modid;

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

    /**
     * Fabric port of the official OBJ cache entry: the geometry parses directly from the obj/mtl resources (the
     * shipped files use exactly the grammar subset the port's OBJ reader supports) and re-bakes per part-visibility
     * set, replacing the official {@code ObjModel.bake(IGeometryBakingContext)} re-bake that rode the NeoForge
     * baking pipeline. Sprite resolution goes through the blocks atlas at render time.
     */
    public static class OBJModelData extends MekanismModelData {

        private static final BlockModelRotation IDENTITY = BlockModelRotation.X0_Y0;

        private TransmitterObjGeometry geometry;
        private TransmitterQuadBaker quadBaker;
        private final Map<Set<String>, List<BakedQuad>> bakedParts = new Object2ObjectOpenHashMap<>();

        protected OBJModelData(ResourceLocation rl) {
            super(rl);
        }

        /** Parses the obj and its material libraries lazily through the client resource manager. */
        private void ensureGeometry() {
            if (quadBaker != null) {
                return;
            }
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            String objSource;
            try (java.io.InputStream stream = resources.getResource(rl).orElseThrow().open()) {
                objSource = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to read OBJ model: " + rl, e);
            }
            Map<String, String> mtlSources = new java.util.LinkedHashMap<>();
            String directory = rl.getPath().contains("/") ? rl.getPath().substring(0, rl.getPath().lastIndexOf('/') + 1) : "";
            for (String line : objSource.split("\n")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2 && parts[0].equals("mtllib")) {
                    ResourceLocation mtlId = ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), directory + parts[1]);
                    try (java.io.InputStream stream = resources.getResource(mtlId).orElseThrow().open()) {
                        mtlSources.put(parts[1], new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException("Failed to read OBJ material library: " + mtlId, e);
                    }
                }
            }
            geometry = TransmitterObjGeometry.parse(objSource, mtlSources, false);
            //The obj texture aliases resolve on the blocks atlas (the official ObjModel baked them the same way).
            TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
            Function<String, TextureAtlasSprite> sprites = alias -> atlas.getSprite(ResourceLocation.parse(alias));
            quadBaker = new TransmitterQuadBaker(geometry, sprites, IDENTITY);
        }

        /** The obj group names, which the armor classes tag-process into part sets. */
        public java.util.Set<String> getRootComponentNames() {
            ensureGeometry();
            return geometry.groups().keySet();
        }

        /** Clears the parsed geometry and part bakes (resource reload - the geometry re-parses lazily). */
        public void clearCached() {
            geometry = null;
            quadBaker = null;
            bakedParts.clear();
        }

        /** Re-bakes the visible parts' quads (the official per-configuration OBJ bake). */
        public List<BakedQuad> getQuads(java.util.Set<String> parts, RandomSource random) {
            ensureGeometry();
            return bakedParts.computeIfAbsent(parts, visible -> {
                List<BakedQuad> quads = new java.util.ArrayList<>();
                for (String group : geometry.groups().keySet()) {
                    if (visible.contains(group)) {
                        quads.addAll(quadBaker.bakeGroup(group, null));
                    }
                }
                return List.copyOf(quads);
            });
        }
    }
}
