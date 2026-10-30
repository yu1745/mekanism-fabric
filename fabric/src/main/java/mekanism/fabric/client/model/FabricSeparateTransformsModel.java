package mekanism.fabric.client.model;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The shipped Meka-Tool's base and left-hand model references, baked through their actual native parents. */
public final class FabricSeparateTransformsModel extends BlockModel {
    private final FabricObjModel base;
    private final Map<ItemDisplayContext, BlockModel> perspectives = new EnumMap<>(ItemDisplayContext.class);

    FabricSeparateTransformsModel(ResourceLocation id, JsonObject root, FabricObjModel base) {
        super(FabricModelJson.parentId(root.toString()), List.of(), FabricModelJson.textures(root.toString()),
              FabricModelJson.ambientOcclusion(root.toString()), FabricModelJson.guiLight(root.toString()),
              FabricModelJson.transforms(root.toString()), FabricModelJson.overrides(root.toString()));
        name = id.toString();
        this.base = base;
        JsonObject declared = root.getAsJsonObject("perspectives");
        for (ItemDisplayContext context : ItemDisplayContext.values()) {
            String key = context.getSerializedName();
            //The shipped Mekanism JSON uses enum-name keys; native display transforms use serialized-name keys.
            if (!declared.has(key)) {
                key = context.name().toLowerCase(Locale.ROOT);
            }
            if (declared.has(key)) {
                perspectives.put(context, BlockModel.fromString(declared.getAsJsonObject(key).toString()));
            }
        }
    }

    @Override
    public BlockModel getRootModel() {
        //Keep the outer generated-item parent for properties without bypassing this custom geometry's bake.
        return this;
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        Collection<ResourceLocation> dependencies = new ArrayList<>(super.getDependencies());
        dependencies.addAll(base.getDependencies());
        perspectives.values().forEach(model -> dependencies.addAll(model.getDependencies()));
        return dependencies;
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
        super.resolveParents(resolver);
        base.resolveParents(resolver);
        perspectives.values().forEach(model -> model.resolveParents(resolver));
    }

    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        Map<ItemDisplayContext, BakedModel> baked = new EnumMap<>(ItemDisplayContext.class);
        perspectives.forEach((context, model) -> baked.put(context, model.bake(baker, sprites, state)));
        ItemOverrides overrides = getOverrides().isEmpty() ? ItemOverrides.EMPTY : new ItemOverrides(baker, this, getOverrides());
        return new Perspectives(base.bake(baker, sprites, state), Map.copyOf(baked), overrides, getGuiLight() == GuiLight.SIDE);
    }

    public static BakedModel forContext(BakedModel model, ItemDisplayContext context) {
        return model instanceof Perspectives separate ? separate.models.getOrDefault(context, separate.base) : model;
    }

    private record Perspectives(BakedModel base, Map<ItemDisplayContext, BakedModel> models, ItemOverrides overrides,
                                boolean usesBlockLight) implements BakedModel {
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) { return base.getQuads(state, side, random); }
        @Override public boolean useAmbientOcclusion() { return base.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return base.isGui3d(); }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return base.getParticleIcon(); }
        @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
        @Override public ItemOverrides getOverrides() { return overrides; }
    }
}
