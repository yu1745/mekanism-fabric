package mekanism.client.model.baked;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.client.render.lib.QuadTransformation;
import mekanism.client.render.lib.QuadUtils;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port of the official extension baked model: the BakedModelWrapper parent and the model-data plumbing drop
 * (no fabric equivalent and no data-dependent users on this port), leaving the keyed quad cache the miner and drive
 * wrappers build on. Vanilla's transform handling rides getTransforms(), so the applyTransform hook goes too.
 */
@NothingNullByDefault
public class ExtensionBakedModel<T> implements BakedModel {

    protected final BakedModel original;

    private final LoadingCache<QuadsKey<T>, List<BakedQuad>> cache = CacheBuilder.newBuilder().build(new CacheLoader<>() {
        @Override
        public List<BakedQuad> load(QuadsKey<T> key) {
            return createQuads(key);
        }
    });

    protected ExtensionBakedModel(BakedModel original) {
        this.original = original;
    }

    @Nullable
    protected QuadsKey<T> createKey(QuadsKey<T> key) {
        return key;
    }

    protected List<BakedQuad> createQuads(QuadsKey<T> key) {
        List<BakedQuad> ret = key.getQuads();
        if (key.getTransformation() != null) {
            ret = QuadUtils.transformBakedQuads(ret, key.getTransformation());
        }
        return ret;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        List<BakedQuad> quads = original.getQuads(state, side, rand);
        QuadsKey<T> key = createKey(new QuadsKey<>(state, side, rand, null, quads));
        if (key == null) {
            return quads;
        }
        return cache.getUnchecked(key);
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
        return original.getOverrides();
    }

    @Override
    public @NotNull ItemTransforms getTransforms() {
        return original.getTransforms();
    }

    public static class LightedBakedModel extends TransformedBakedModel<Void> {

        public LightedBakedModel(BakedModel original) {
            super(original, QuadTransformation.filtered_fullbright);
        }
    }

    public static class TransformedBakedModel<T> extends ExtensionBakedModel<T> {

        private final QuadTransformation transform;

        public TransformedBakedModel(BakedModel original, QuadTransformation transform) {
            super(original);
            this.transform = transform;
        }

        @Nullable
        @Override
        protected QuadsKey<T> createKey(QuadsKey<T> key) {
            return key.transform(transform);
        }
    }

    public static class QuadsKey<T> {

        @Nullable
        private final BlockState state;
        @Nullable
        private final Direction side;
        private final RandomSource random;
        @Nullable
        private final RenderType layer;
        private final List<BakedQuad> quads;
        @Nullable
        private QuadTransformation transformation;

        @Nullable
        private T data;
        private int dataHash;
        @Nullable
        private BiPredicate<T, T> equality;

        public QuadsKey(@Nullable BlockState state, @Nullable Direction side, RandomSource random, @Nullable RenderType layer, List<BakedQuad> quads) {
            this.state = state;
            this.side = side;
            this.random = random;
            this.layer = layer;
            this.quads = quads;
        }

        public QuadsKey<T> transform(Supplier<? extends QuadTransformation> transformation) {
            return transform(transformation.get());
        }

        public QuadsKey<T> transform(QuadTransformation transformation) {
            this.transformation = transformation;
            return this;
        }

        public QuadsKey<T> data(T data, int dataHash, BiPredicate<T, T> equality) {
            this.data = data;
            this.dataHash = dataHash;
            this.equality = equality;
            return this;
        }

        @Nullable
        public BlockState getBlockState() {
            return state;
        }

        @Nullable
        public Direction getSide() {
            return side;
        }

        public RandomSource getRandom() {
            return random;
        }

        @Nullable
        public RenderType getLayer() {
            return layer;
        }

        public List<BakedQuad> getQuads() {
            return quads;
        }

        @Nullable
        public QuadTransformation getTransformation() {
            return transformation;
        }

        @Nullable
        public T getData() {
            return data;
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(state);
            result = 31 * result + Objects.hashCode(side);
            result = 31 * result + Objects.hashCode(layer);
            result = 31 * result + Objects.hashCode(transformation);
            result = 31 * result + dataHash;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof QuadsKey<?> other)) {
                return false;
            } else if (side != other.side || layer != other.layer || !Objects.equals(state, other.state)) {
                return false;
            } else if (transformation != null && !transformation.equals(other.transformation)) {
                return false;
            }
            return data == null || equality != null && equality.test(data, (T) other.getData());
        }
    }
}
