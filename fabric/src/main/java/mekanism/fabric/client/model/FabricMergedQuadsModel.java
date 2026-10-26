package mekanism.fabric.client.model;

import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The shared baked form of every adapter loader model: one immutable quad list (composite union or OBJ geometry)
 * with the official transform/particle/lighting properties. Chunk and item rendering both request quads with a null
 * side, and cull faces are carried per-quad, so the list is returned verbatim for every query.
 */
public final class FabricMergedQuadsModel implements BakedModel {

    private final ResourceLocation id;
    private final List<BakedQuad> quads;
    private final TextureAtlasSprite particle;
    private final ItemTransforms transforms;
    private final boolean ambientOcclusion;
    private final boolean gui3d;
    private final boolean usesBlockLight;

    FabricMergedQuadsModel(ResourceLocation id, List<BakedQuad> quads, TextureAtlasSprite particle, ItemTransforms transforms,
          boolean ambientOcclusion, boolean gui3d, boolean usesBlockLight) {
        this.id = id;
        this.quads = List.copyOf(quads);
        this.particle = particle;
        this.transforms = transforms;
        this.ambientOcclusion = ambientOcclusion;
        this.gui3d = gui3d;
        this.usesBlockLight = usesBlockLight;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return quads;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return ambientOcclusion;
    }

    @Override
    public boolean isGui3d() {
        return gui3d;
    }

    @Override
    public boolean usesBlockLight() {
        return usesBlockLight;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @Override
    public ItemTransforms getTransforms() {
        return transforms;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
