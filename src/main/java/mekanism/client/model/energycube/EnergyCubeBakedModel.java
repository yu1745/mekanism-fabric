package mekanism.client.model.energycube;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import mekanism.api.RelativeSide;
import mekanism.client.model.energycube.EnergyCubeGeometry.FaceData;
import mekanism.client.render.lib.QuadTransformation;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.TileEntityEnergyCube.CubeSideState;
import mekanism.common.util.EnumUtils;
import net.minecraft.Util;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port of the official energy cube dynamic baked model. The NeoForge {@code IDynamicBakedModel} shell (render
 * type sets, item render types, particle/transforms pass-through) becomes a plain quad source: the static block model
 * carries the frame, and {@link RenderEnergyCube} on the Fabric port draws the side LEDs and ports from
 * {@link #getQuads} using the tile's render data - the same per-side selection {@code createQuads} performed.
 */
public class EnergyCubeBakedModel {

    private static final CubeSideState[] INACTIVE = Util.make(new CubeSideState[EnumUtils.DIRECTIONS.length], sideStates -> Arrays.fill(sideStates, CubeSideState.INACTIVE));
    private static final QuadTransformation LED_TRANSFORMS = QuadTransformation.list(QuadTransformation.fullbright, QuadTransformation.uvShift(-0.125F, 0));

    private final LoadingCache<QuadKey, QuadSplits> cache = CacheBuilder.newBuilder().build(new CacheLoader<>() {
        @NotNull
        @Override
        public QuadSplits load(@NotNull QuadKey key) {
            return createQuads(key);
        }
    });

    private final FaceData frame;
    private final Map<RelativeSide, FaceData> leds;
    private final Map<RelativeSide, FaceData> ports;
    private final Map<RelativeSide, FaceData> activeLEDs;
    private final Map<RelativeSide, FaceData> activePorts;
    @Nullable
    private final TextureAtlasSprite particle;

    EnergyCubeBakedModel(FaceData frame, Map<RelativeSide, FaceData> leds, Map<RelativeSide, FaceData> ports) {
        this(frame, leds, ports, null);
    }

    EnergyCubeBakedModel(FaceData frame, Map<RelativeSide, FaceData> leds, Map<RelativeSide, FaceData> ports, @Nullable TextureAtlasSprite particle) {
        this.frame = frame;
        this.leds = leds;
        this.ports = ports;
        this.activeLEDs = new EnumMap<>(RelativeSide.class);
        this.activePorts = new EnumMap<>(RelativeSide.class);
        //Note: We don't bother having any form of lazy transformations take place here as this should only have a memory
        // impact equivalent to having two models: one with the leds and ports off, and one with all of them active
        for (Map.Entry<RelativeSide, FaceData> entry : this.leds.entrySet()) {
            activeLEDs.put(entry.getKey(), entry.getValue().transform(LED_TRANSFORMS));
        }
        for (Map.Entry<RelativeSide, FaceData> entry : this.ports.entrySet()) {
            activePorts.put(entry.getKey(), entry.getValue().transform(QuadTransformation.filtered_fullbright));
        }
        this.particle = particle;
    }

    @Nullable
    public TextureAtlasSprite getParticle() {
        return particle;
    }

    /** The static frame quads the block model bakes with (the dynamic sides live in the BER). */
    public List<BakedQuad> getFrameQuads(@Nullable Direction side) {
        return frame.getFaces(side);
    }

    /** The inactive-state LED quads (the official default when there is no side data). */
    public List<BakedQuad> getInactiveLEDQuads(@Nullable Direction side) {
        List<BakedQuad> quads = new ArrayList<>();
        for (RelativeSide dir : EnumUtils.SIDES) {
            quads.addAll(leds.get(dir).getFaces(side));
        }
        return quads;
    }

    /**
     * The official per-side selection split by draw lighting: the fabric BER draws the lit half with full brightness
     * (the official fullbright quad transformation is preserved) and the unlit half with the world light, instead of
     * NeoForge's single mixed quad list over the chunk pipeline.
     */
    @NotNull
    public QuadSplits getSplitQuads(@Nullable Direction side, @Nullable CubeSideState[] sideStates) {
        if (sideStates == null || sideStates.length != EnumUtils.SIDES.length) {
            //If there is no side data then treat everything as inactive
            sideStates = INACTIVE;
        }
        //Note: We intentionally ignore the state and use null here to minimize cache size as it doesn't actually matter
        // or get used for energy cube models
        return cache.getUnchecked(new QuadKey(side, sideStates));
    }

    /** Convenience for the static block/item model: the all-inactive selection (frame excluded). */
    @NotNull
    public QuadSplits getInactiveQuads(@Nullable Direction side) {
        return getSplitQuads(side, null);
    }

    private QuadSplits createQuads(QuadKey key) {
        Direction side = key.side();
        CubeSideState[] data = Objects.requireNonNull(key.sideStates());
        List<BakedQuad> lit = new ArrayList<>();
        List<BakedQuad> unlit = new ArrayList<>();
        for (int i = 0; i < EnumUtils.SIDES.length; i++) {
            RelativeSide dir = EnumUtils.SIDES[i];
            CubeSideState sideState = data[i];
            if (sideState == CubeSideState.ACTIVE_LIT) {
                lit.addAll(activeLEDs.get(dir).getFaces(side));
                lit.addAll(activePorts.get(dir).getFaces(side));
            } else {
                unlit.addAll(leds.get(dir).getFaces(side));
                if (sideState == CubeSideState.ACTIVE_UNLIT) {
                    unlit.addAll(ports.get(dir).getFaces(side));
                }
            }
        }
        return new QuadSplits(lit, unlit);
    }

    public record QuadSplits(List<BakedQuad> lit, List<BakedQuad> unlit) {
    }

    private record QuadKey(@Nullable Direction side, CubeSideState[] sideStates) {

        private QuadKey {
            sideStates = sideStates.clone();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof QuadKey other && other.side == side && Arrays.equals(other.sideStates, sideStates);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(sideStates) + Objects.hashCode(side);
        }
    }
}
