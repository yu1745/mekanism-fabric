package mekanism.fabric.client.render.obj;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import mekanism.client.model.data.TransmitterModelData;
import mekanism.client.model.data.TransmitterModelData.Diversion;
import mekanism.common.lib.transmitter.ConnectionType;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Fabric consumer for the transmitter block model: a real vanilla {@link BakedModel} (so it is the actual result of
 * the model loader) that additionally implements {@link FabricBakedModel} for chunk meshing. During meshing the block
 * view is a Fabric {@link RenderAttachedBlockView}, and the attachment (the immutable {@link TransmitterModelData}
 * snapshot published by {@code TileEntityTransmitter}) is the only data source consulted — never the live tile.
 *
 * <p>Six-part selection, end-icon rotation, opaque texture remapping, glass/cutout split (internal parts emit with a
 * CUTOUT material, glass with TRANSLUCENT) and material tint index 1 are preserved from the official implementation;
 * the geometry comes from {@link TransmitterObjGeometry} parsed off the shipped assets, and quads are produced by
 * {@link TransmitterQuadBaker}. Texture aliases are compared/stripped consistently without the JSON {@code '#'}
 * prefix: the parser already strips it from MTL {@code map_Kd} references.</p>
 */
public final class FabricTransmitterBakedModel implements BakedModel, FabricBakedModel {

    /** Alias fragment set by the JSON model's child tier texture resolution; kept here as the contract constant. */
    public static final String GLASS_ALIAS_MARKER = "glass";

    private static final RenderMaterial CUTOUT_MATERIAL = material(BlendMode.CUTOUT);
    private static final RenderMaterial TRANSLUCENT_MATERIAL = material(BlendMode.TRANSLUCENT);

    private static RenderMaterial material(BlendMode blendMode) {
        MaterialFinder finder = RendererAccess.INSTANCE.getRenderer().materialFinder();
        RenderMaterial material = finder.blendMode(blendMode).find();
        finder.clear();
        return material;
    }

    private final TransmitterObjGeometry internalGeometry;
    @Nullable
    private final TransmitterObjGeometry glassGeometry;
    private final Function<String, TextureAtlasSprite> spriteLookup;
    private final boolean opaqueTransmitters;
    private final boolean hasGlass;
    private final TextureAtlasSprite particleIcon;
    private final ItemTransforms transforms;
    private final ModelState modelState;
    private final boolean ambientOcclusion;
    private final boolean blockLight;
    private final ItemOverrides overrides;

    private final LoadingCache<SidedConnection, List<BakedQuad>> internalParts;
    @Nullable
    private final LoadingCache<SidedConnection, List<BakedQuad>> glassParts;
    private final List<BakedQuad> defaultItemQuads;

    public FabricTransmitterBakedModel(TransmitterObjGeometry internalGeometry, @Nullable TransmitterObjGeometry glassGeometry,
          Function<String, TextureAtlasSprite> spriteLookup, boolean opaqueTransmitters, ItemTransforms transforms,
          ModelState modelState, boolean ambientOcclusion, boolean blockLight, ItemOverrides overrides) {
        this.internalGeometry = internalGeometry;
        this.glassGeometry = glassGeometry;
        this.spriteLookup = alias -> spriteLookup0(spriteLookup, alias);
        this.opaqueTransmitters = opaqueTransmitters;
        this.hasGlass = glassGeometry != null;
        this.transforms = transforms;
        this.modelState = modelState;
        this.ambientOcclusion = ambientOcclusion;
        this.blockLight = blockLight;
        this.overrides = overrides;
        this.particleIcon = this.spriteLookup.apply("particle");
        this.internalParts = CacheBuilder.newBuilder().build(createPartCacheLoader(internalGeometry, false));
        this.glassParts = glassGeometry == null ? null : CacheBuilder.newBuilder().build(createPartCacheLoader(glassGeometry, true));
        //Default arrangement = every side NONE, used for items and missing attachments
        List<BakedQuad> quads = new ArrayList<>();
        TransmitterQuadBaker itemBaker = new TransmitterQuadBaker(internalGeometry, this.spriteLookup, modelState);
        for (Direction side : Direction.values()) {
            // Official default item uses VisibleModelConfiguration, not the block-only opaque/icon remapper.
            quads.addAll(itemBaker.bakeGroup(getPartName(side, ConnectionType.NONE), null));
        }
        this.defaultItemQuads = Collections.unmodifiableList(quads);
    }

    private static TextureAtlasSprite spriteLookup0(Function<String, TextureAtlasSprite> lookup, String alias) {
        TextureAtlasSprite sprite = lookup.apply(alias);
        if (sprite == null) {
            throw new IllegalStateException("FabricTransmitterBakedModel: missing sprite for alias '" + alias + "'");
        }
        return sprite;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos, java.util.function.Supplier<RandomSource> randomSupplier, RenderContext context) {
        Object attachment = blockView instanceof RenderAttachedBlockView renderAttachedView
              ? renderAttachedView.getBlockEntityRenderAttachment(pos) : null;
        if (attachment instanceof TransmitterModelData data) {
            //The producer is required to publish immutable snapshots; this consumer only reads them
            emit(data, context);
        } else {
            emitQuads(defaultItemQuads, CUTOUT_MATERIAL, context);
        }
    }

    private void emit(TransmitterModelData data, RenderContext context) {
        for (Direction side : Direction.values()) {
            ConnectionType connectionType = data.getConnectionType(side);
            IconStatus iconStatus = getIconStatus(data, side, connectionType);
            emitQuads(internalParts.getUnchecked(new SidedConnection(side, connectionType, iconStatus)), CUTOUT_MATERIAL, context);
            if (hasGlass && data.getHasColor()) {
                //Glass pass is only meaningful when there is a color to tint it with; internal (cutout) parts always render
                emitQuads(Objects.requireNonNull(glassParts).getUnchecked(new SidedConnection(side, connectionType, iconStatus)), TRANSLUCENT_MATERIAL, context);
            }
        }
    }

    @Override
    public void emitItemQuads(ItemStack stack, java.util.function.Supplier<RandomSource> randomSupplier, RenderContext context) {
        emitQuads(defaultItemQuads, CUTOUT_MATERIAL, context);
    }

    /** Copies each baked quad (vertex data, tint index) onto the emitter with the given material and actually emits it. */
    private static void emitQuads(List<BakedQuad> quads, RenderMaterial material, RenderContext context) {
        QuadEmitter emitter = context.getEmitter();
        for (BakedQuad quad : quads) {
            //Cull face null: emitter quads are never culled by vanilla face culling; tint index travels in the vertex data
            emitter.fromVanilla(quad, material, null);
            emitter.emit();
        }
    }

    // ---- vanilla BakedModel: the block/item render fallbacks that do not have a render attachment ----

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        //Unculled geometry only; the attachment-aware block path goes through emitBlockQuads above
        return side == null ? defaultItemQuads : Collections.emptyList();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return ambientOcclusion;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return blockLight;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particleIcon;
    }

    @Override
    public ItemTransforms getTransforms() {
        //The JSON "display" block is inherited through the real vanilla BlockModel chain by the loader and handed in here, so
        //gui/ground/fixed/hand transforms keep whatever the shipped model declared (including the parent's copy).
        return transforms;
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    private CacheLoader<SidedConnection, List<BakedQuad>> createPartCacheLoader(TransmitterObjGeometry geometry, boolean glass) {
        return new CacheLoader<>() {
            @Override
            public List<BakedQuad> load(SidedConnection key) {
                Direction side = key.side();
                ConnectionType connectionType = key.connection();
                IconStatus iconStatus = key.status();
                String part = getPartName(side, connectionType);
                if (!geometry.groups().containsKey(part)) {
                    //Mirror the official short-circuit: a missing part contributes no geometry
                    return Collections.emptyList();
                }
                Quaternionf rotation = null;
                if (connectionType == ConnectionType.NONE && iconStatus.getAngle() > 0) {
                    Vector3f axis = new Vector3f(-side.getNormal().getX(), -side.getNormal().getY(), -side.getNormal().getZ());
                    rotation = new Quaternionf().setAngleAxis(iconStatus.getAngle(), axis.x, axis.y, axis.z);
                }
                TransmitterQuadBaker baker = new TransmitterQuadBaker(geometry, alias -> spriteLookup.apply(adjustAlias(alias, part, side, iconStatus, glass)), modelState);
                return baker.bakeGroup(part, rotation);
            }
        };
    }

    /**
     * Port of the official {@code TransmitterModelConfiguration.adjustTextureName}: per-part side textures collapse to
     * the shared side texture while an icon shows, and opaque transmitter config remaps to the opaque variants.
     * Aliases are normalized WITHOUT the JSON {@code '#'} prefix (the MTL parser strips it), and the sprite lookup
     * receives the same normalized form.
     */
    private String adjustAlias(String alias, String part, Direction side, IconStatus iconStatus, boolean glass) {
        String name = alias;
        if (directionForPiece(alias) != null) {
            if (iconStatus != IconStatus.NO_SHOW) {
                name = name.contains(GLASS_ALIAS_MARKER) ? "side_glass" : "side";
            }
            if (opaqueTransmitters) {
                if (name.startsWith("side")) {
                    return name + "_opaque";
                } else if (name.startsWith("center")) {
                    return name.contains(GLASS_ALIAS_MARKER) ? "center_glass_opaque" : "center_opaque";
                }
            }
            return name;
        } else if (opaqueTransmitters && name.startsWith("side")) {
            return name + "_opaque";
        }
        return name;
    }

    private static Direction directionForPiece(String piece) {
        for (Direction side : Direction.values()) {
            if (piece.endsWith(side.getSerializedName())) {
                return side;
            }
        }
        return null;
    }

    private static String getPartName(Direction side, ConnectionType connectionType) {
        return side.getSerializedName() + connectionType.name();
    }

    /** Port of the official end-icon status selection (Diversion transporters never show icons). */
    public static IconStatus getIconStatus(TransmitterModelData modelData, Direction side, ConnectionType connectionType) {
        if (modelData instanceof Diversion || connectionType != ConnectionType.NONE) {
            return IconStatus.NO_SHOW;
        }
        return switch (side) {
            case DOWN, UP -> getStatus(modelData, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
            case NORTH, SOUTH -> getStatus(modelData, Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST);
            case WEST, EAST -> getStatus(modelData, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH);
        };
    }

    private static IconStatus getStatus(TransmitterModelData modelData, Direction a, Direction b, Direction c, Direction d) {
        boolean hasA = modelData.getConnectionType(a) != ConnectionType.NONE;
        boolean hasB = modelData.getConnectionType(b) != ConnectionType.NONE;
        boolean hasC = modelData.getConnectionType(c) != ConnectionType.NONE;
        boolean hasD = modelData.getConnectionType(d) != ConnectionType.NONE;
        if ((hasA || hasB) != (hasC || hasD)) {
            if (hasA && hasB) {
                return IconStatus.NO_ROTATION;
            } else if (hasC && hasD) {
                return IconStatus.ROTATE_270;
            }
        }
        return IconStatus.NO_SHOW;
    }

    /** Official end-icon statuses; angle in radians. */
    public enum IconStatus {
        NO_ROTATION(0),
        ROTATE_270(270),
        NO_SHOW(0);

        private final float angle;

        IconStatus(float angle) {
            this.angle = angle * Mth.DEG_TO_RAD;
        }

        public float getAngle() {
            return angle;
        }
    }

    private record SidedConnection(Direction side, ConnectionType connection, IconStatus status) {
    }
}
