package mekanism.client.model.robit;

import java.util.List;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.client.RobitSpriteUploader;
import mekanism.client.render.lib.QuadTransformation;
import mekanism.client.render.lib.QuadTransformation.TextureFilteredTransformation;
import mekanism.client.render.lib.QuadUtils;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port of the official robit baked model. The NeoForge extension-override shell (model data driven side
 * selection and the extended item override framework) collapses to the two surfaces the robit needs on Fabric:
 * the per-skin texture transform of the missingno placeholder quads, and the robit atlas render type. The robit item
 * renders through the builtin renderer (reading the stack's skin component), and the entity renders through
 * {@code RenderRobit}, so no model-data path survives.
 */
@NothingNullByDefault
public class RobitBakedModel implements BakedModel {

    private final BakedModel original;
    @Nullable
    private final ResourceLocation skinTexture;

    public RobitBakedModel(BakedModel original) {
        this(original, null);
    }

    public RobitBakedModel(BakedModel original, @Nullable ResourceLocation skinTexture) {
        this.original = original;
        this.skinTexture = skinTexture;
    }

    /**
     * The official per-skin texture transform: replaces the missingno placeholder sprites (the shipped models declare
     * an unmapped {@code #robit} reference) with the skin's current texture.
     */
    public static List<BakedQuad> transformForSkin(List<BakedQuad> quads, @Nullable ResourceLocation skinTexture) {
        if (skinTexture != null && RobitSpriteUploader.UPLOADER != null) {
            QuadTransformation transformation = QuadTransformation.texture(RobitSpriteUploader.UPLOADER.getSprite(skinTexture));
            transformation = TextureFilteredTransformation.of(transformation, rl -> rl.getPath().equals("missingno"));
            return QuadUtils.transformBakedQuads(quads, transformation);
        }
        return quads;
    }

    /** Vanilla putBulkData ignores the light encoded in baked vertices; preserve the model's per-face minimum. */
    public static int lightFor(BakedQuad quad, int ambientLight) {
        int block = net.minecraft.client.renderer.LightTexture.block(ambientLight);
        int sky = net.minecraft.client.renderer.LightTexture.sky(ambientLight);
        for (int vertex = 0; vertex < 4; vertex++) {
            int baked = quad.getVertices()[vertex * 8 + 6];
            block = Math.max(block, net.minecraft.client.renderer.LightTexture.block(baked));
            sky = Math.max(sky, net.minecraft.client.renderer.LightTexture.sky(baked));
        }
        return net.minecraft.client.renderer.LightTexture.pack(block, sky);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        return transformForSkin(original.getQuads(state, side, rand), skinTexture);
    }

    /** The robit atlas is the official render surface for every robit quad. */
    public static List<RenderType> getRenderTypes() {
        return RobitSpriteUploader.RENDER_TYPES;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return original.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return original.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return original.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return original.getParticleIcon();
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public @NotNull net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
        return original.getTransforms();
    }
}
