package mekanism.fabric.client.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * Shared baked form of loader models, with separate unculled and direction-culled quad lists.
 */
public final class FabricMergedQuadsModel implements BakedModel {

    private final ResourceLocation id;
    private final List<BakedQuad> quads;
    private final Map<Direction, List<BakedQuad>> culledQuads;
    private final TextureAtlasSprite particle;
    private final ItemTransforms transforms;
    private final boolean ambientOcclusion;
    private final boolean gui3d;
    private final boolean usesBlockLight;

    FabricMergedQuadsModel(ResourceLocation id, List<BakedQuad> quads, TextureAtlasSprite particle, ItemTransforms transforms,
          boolean ambientOcclusion, boolean gui3d, boolean usesBlockLight) {
        this(id, quads, Map.of(), particle, transforms, ambientOcclusion, gui3d, usesBlockLight);
    }

    FabricMergedQuadsModel(ResourceLocation id, List<BakedQuad> quads, Map<Direction, List<BakedQuad>> culledQuads,
          TextureAtlasSprite particle, ItemTransforms transforms, boolean ambientOcclusion, boolean gui3d, boolean usesBlockLight) {
        this.id = id;
        this.quads = List.copyOf(quads);
        Map<Direction, List<BakedQuad>> copied = new EnumMap<>(Direction.class);
        culledQuads.forEach((direction, faces) -> copied.put(direction, List.copyOf(faces)));
        this.culledQuads = Map.copyOf(copied);
        this.particle = particle;
        this.transforms = transforms;
        this.ambientOcclusion = ambientOcclusion;
        this.gui3d = gui3d;
        this.usesBlockLight = usesBlockLight;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return side == null ? quads : culledQuads.getOrDefault(side, List.of());
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
