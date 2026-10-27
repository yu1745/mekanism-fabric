package mekanism.fabric.client.model;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * {@code neoforge:composite} semantics: the model bakes as the union of its declared inline children. The outer JSON
 * keeps supplying the shared transforms, particle texture and lighting defaults; every child is a plain vanilla
 * {@link BlockModel} (factories declare a {@code parent} plus per-child texture aliases), so baking reuses the
 * vanilla pipeline end to end and the merged model only concatenates the resulting quads.
 *
 * <p>Extends BlockModel because vanilla's parent resolution hard-requires BlockModel parents - the official factory
 * ITEM models parent onto these composite BLOCK models ({@code FabricTransmitterBlockModel} documents the same
 * constraint from the transmitter lane).</p>
 */
public final class FabricCompositeModel extends BlockModel {

    private final ResourceLocation id;
    private final List<Pair<String, BlockModel>> children;
    private boolean resolvingParents;

    FabricCompositeModel(ResourceLocation id, String rootJson, List<Pair<String, BlockModel>> children) {
        super(FabricModelJson.parentId(rootJson), List.<BlockElement>of(), FabricModelJson.textures(rootJson),
              FabricModelJson.ambientOcclusion(rootJson), FabricModelJson.guiLight(rootJson),
              FabricModelJson.transforms(rootJson), FabricModelJson.overrides(rootJson));
        this.id = id;
        this.children = List.copyOf(children);
        //Keep the vanilla debug name populated the way the vanilla loader does.
        this.name = id.toString();
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        List<ResourceLocation> dependencies = new ArrayList<>(super.getDependencies());
        for (Pair<String, BlockModel> child : children) {
            dependencies.addAll(child.getSecond().getDependencies());
        }
        return dependencies;
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
        if (resolvingParents) {
            throw new IllegalStateException("Composite model child cycle at " + id);
        }
        resolvingParents = true;
        try {
            super.resolveParents(resolver);
            for (Pair<String, BlockModel> child : children) {
                child.getSecond().resolveParents(resolver);
            }
        } finally {
            resolvingParents = false;
        }
    }

    @Override
    public BakedModel bake(@Nullable ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        return bakeInherited(this, baker, spriteGetter, state);
    }

    /** Bake this geometry with the actual descendant model as the property and override owner. */
    public BakedModel bakeInherited(BlockModel owner, @Nullable ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        //Each child bakes through the vanilla pipeline with its own parent chain and texture aliases (the official
        //active variants override a child texture); the union of their quads is the composite.
        RandomSource random = RandomSource.create();
        List<BakedQuad> quads = new ArrayList<>();
        Map<Direction, List<BakedQuad>> culledQuads = new EnumMap<>(Direction.class);
        for (Pair<String, BlockModel> child : children) {
            BakedModel baked = child.getSecond().bake(baker, spriteGetter, state);
            quads.addAll(baked.getQuads(null, null, random));
            for (Direction direction : Direction.values()) {
                culledQuads.computeIfAbsent(direction, ignored -> new ArrayList<>()).addAll(baked.getQuads(null, direction, random));
            }
        }
        //The descendant's properties and item overrides remain authoritative even when geometry is inherited.
        ItemOverrides overrides = owner.getOverrides().isEmpty() ? ItemOverrides.EMPTY
              : new ItemOverrides(Objects.requireNonNull(baker, "baker required for item overrides"), owner, owner.getOverrides());
        return new FabricMergedQuadsModel(id, quads, culledQuads, spriteGetter.apply(owner.getMaterial("particle")), owner.getTransforms(), overrides,
              owner.hasAmbientOcclusion(), true, owner.getGuiLight() == BlockModel.GuiLight.SIDE);
    }
}
