package mekanism.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import mekanism.api.RelativeSide;
import mekanism.client.render.MekanismRenderer.Model3D;
import mekanism.client.render.RenderResizableCuboid.FaceDisplay;
import mekanism.client.render.lib.Outlines;
import mekanism.client.render.lib.Outlines.Line;
import mekanism.client.render.tileentity.IWireFrameRenderer;
import mekanism.common.base.ProfilerConstants;
import mekanism.common.block.BlockBounding;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeCustomSelectionBox;
import mekanism.common.content.gear.IBlastingItem;
import mekanism.common.item.ItemConfigurator;
import mekanism.common.item.ItemConfigurator.ConfiguratorMode;
import mekanism.common.lib.effect.BoltEffect;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.WorldUtils;
import mekanism.fabric.client.render.FabricBoltRendering;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Level-render half of the official NeoForge event hub: the transparent {@link LazyRender} queue (drained at the
 * after-translucent-blocks stage by the Fabric bootstrap registration), the wire-frame helpers shared with the tile
 * renderers, and the block-highlight handler including configurator overlays. Other listeners of the official
 * file are handled by their respective lanes — see
 * {@code docs/TASK052_RESUME_2026-10-26.md}.
 */
public class RenderTickHandler {

    public static final Minecraft minecraft = Minecraft.getInstance();

    private static final Map<BlockState, List<Line>> cachedWireFrames = new Reference2ObjectOpenHashMap<>();
    private static final Map<Direction, Map<TransmissionType, Model3D>> cachedOverlays = new EnumMap<>(Direction.class);
    private static final List<LazyRender> transparentRenderers = new ArrayList<>();

    public static void clearQueued() {
        //RadiationOverlay (HUD lane) resets when its own lane lands; the queue itself is what the BER spine needs.
        transparentRenderers.clear();
    }

    public static void resetCached() {
        cachedOverlays.clear();
        cachedWireFrames.clear();
    }

    public static void renderBolt(Object renderer, BoltEffect bolt) {
        FabricBoltRendering.renderBolt(renderer, bolt);
    }

    public static void addTransparentRenderer(LazyRender render) {
        transparentRenderers.add(render);
    }

    /**
     * Fabric drain point for the official {@code RenderLevelStageEvent} AFTER_TRANSLUCENT_BLOCKS handler: the
     * bootstrap registers it against {@code WorldRenderEvents.AFTER_TRANSLUCENT}, which fires exactly once per frame
     * at the matching stage.
     */
    public static void renderTransparentLayer(Camera camera, PoseStack poseStack, int renderTick, float partialTick, ProfilerFiller profiler) {
        //Only do matrix transforms and mess with buffers if we actually have any renders to render
        if (!transparentRenderers.isEmpty()) {
            MultiBufferSource.BufferSource renderer = minecraft.renderBuffers().bufferSource();
            profiler.push(ProfilerConstants.DELAYED);
            if (transparentRenderers.size() == 1) {
                //If we only have one render type we don't need to bother calculating any distances
                LazyRender lazyRender = transparentRenderers.getFirst();
                doTransparentRender(lazyRender.getRenderType(), lazyRender, camera, renderer, poseStack, renderTick, partialTick, profiler);
            } else {
                for (LazyRender render : transparentRenderers) {
                    Vec3 renderPos = render.getCenterPos(partialTick);
                    //Note: We can just use the distance sqr as we use it for both things, so they compare the same anyway
                    render.distance = camera.getPosition().distanceToSqr(renderPos);
                }
                //Sort in the order of furthest to closest (reverse of by closest)
                transparentRenderers.sort(Comparator.comparingDouble(info -> -info.distance));
                for (LazyRender render : transparentRenderers) {
                    doTransparentRender(render.getRenderType(), render, camera, renderer, poseStack, renderTick, partialTick, profiler);
                }
            }
            renderer.endBatch();
            transparentRenderers.clear();
            profiler.pop();
        }
    }

    private boolean outliningArea;

    /** Returns false when Mekanism replaces the vanilla outline. */
    public boolean onBlockHover(WorldRenderContext context, WorldRenderContext.BlockOutlineContext outline) {
        Player player = minecraft.player;
        if (player == null || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return true;
        }
        Level world = player.level();
        BlockPos pos = outline.blockPos();
        BlockState state = outline.blockState();
        PoseStack matrix = Objects.requireNonNull(context.matrixStack());
        MultiBufferSource renderer = Objects.requireNonNull(context.consumers());
        Camera camera = context.camera();
        ProfilerFiller profiler = world.getProfiler();

        profiler.push(ProfilerConstants.AREA_MINE_OUTLINE);
        if (!outliningArea) {
            ItemStack stack = player.getMainHandItem();
            if (!stack.isEmpty() && stack.getItem() instanceof IBlastingItem tool) {
                Map<BlockPos, BlockState> blocks = tool.getBlastedBlocks(world, player, stack, pos, state);
                outliningArea = true;
                try {
                    for (Entry<BlockPos, BlockState> block : blocks.entrySet()) {
                        BlockPos target = block.getKey();
                        if (!pos.equals(target)) {
                            // Dispatch the ordinary Fabric outline event for each extra target, as the original
                            // NeoForge code dispatches a highlight event before falling back to vanilla drawing.
                            var extra = new ExtraBlockOutline(outline, target, block.getValue(), renderer);
                            if (WorldRenderEvents.BLOCK_OUTLINE.invoker().onBlockOutline(context, extra)) {
                                LevelRenderer.renderShape(matrix, renderer.getBuffer(RenderType.lines()),
                                      block.getValue().getShape(world, target, CollisionContext.of(player)),
                                      target.getX() - outline.cameraX(), target.getY() - outline.cameraY(), target.getZ() - outline.cameraZ(),
                                      0, 0, 0, 0.4F);
                            }
                        }
                    }
                } finally {
                    outliningArea = false;
                }
            }
        }
        profiler.pop();

        boolean replace = false;
        profiler.push(ProfilerConstants.MEKANISM_OUTLINE);
        if (!state.isAir() && world.getWorldBorder().isWithinBounds(pos)) {
            BlockPos actualPos = pos;
            BlockState actualState = state;
            if (state.is(MekanismBlocks.BOUNDING_BLOCK)) {
                BlockPos mainPos = BlockBounding.getMainBlockPos(world, pos);
                if (mainPos != null) {
                    actualPos = mainPos;
                    actualState = world.getBlockState(mainPos);
                }
            }
            AttributeCustomSelectionBox custom = Attribute.get(actualState, AttributeCustomSelectionBox.class);
            if (custom != null) {
                BlockEntity tile = custom.isJavaModel() ? WorldUtils.getTileEntity(world, actualPos) : null;
                BlockEntityRenderer<BlockEntity> tileRenderer = tile == null ? null : minecraft.getBlockEntityRenderDispatcher().getRenderer(tile);
                if (!custom.isJavaModel() || tileRenderer instanceof IWireFrameRenderer wireFrame && wireFrame.hasSelectionBox(actualState)) {
                    matrix.pushPose();
                    Vec3 view = camera.getPosition();
                    matrix.translate(actualPos.getX() - view.x, actualPos.getY() - view.y, actualPos.getZ() - view.z);
                    VertexConsumer buffer = renderer.getBuffer(RenderType.lines());
                    if (!custom.isJavaModel()) {
                        renderQuadsWireFrame(actualState, buffer, matrix, world.random);
                    } else {
                        IWireFrameRenderer wireFrame = (IWireFrameRenderer) tileRenderer;
                        if (wireFrame.isCombined()) {
                            renderQuadsWireFrame(actualState, buffer, matrix, world.random);
                        }
                        wireFrame.renderWireFrame(tile, context.tickCounter().getGameTimeDeltaPartialTick(false), matrix, buffer);
                    }
                    matrix.popPose();
                    replace = true;
                }
            }
        }
        profiler.pop();

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ItemConfigurator)) {
            stack = player.getOffhandItem();
        }
        if (!stack.isEmpty() && stack.getItem() instanceof ItemConfigurator configurator) {
            profiler.push(ProfilerConstants.CONFIGURABLE_MACHINE);
            ConfiguratorMode mode = configurator.getMode(stack);
            if (mode.isConfigurating()) {
                TransmissionType type = Objects.requireNonNull(mode.getTransmission(), "Configurating state requires transmission type");
                BlockEntity tile = WorldUtils.getTileEntity(world, pos);
                if (tile instanceof ISideConfiguration configurable) {
                    TileComponentConfig config = configurable.getConfig();
                    ConfigInfo configInfo = config.supports(type) ? config.getConfig(type) : null;
                    if (configInfo != null) {
                        Direction face = hit.getDirection();
                        RelativeSide side = RelativeSide.fromDirections(configurable.getDirection(), face);
                        if (configInfo.isSideEnabled(side)) {
                            Vec3 view = camera.getPosition();
                            matrix.pushPose();
                            matrix.translate(pos.getX() - view.x, pos.getY() - view.y, pos.getZ() - view.z);
                            Model3D object = getOverlayModel(face, type);
                            VertexConsumer buffer = renderer.getBuffer(Sheets.translucentCullBlockSheet());
                            int argb = MekanismRenderer.getColorARGB(configInfo.getDataType(side).getColor(), 0.6F);
                            if (object != null) {
                                RenderResizableCuboid.renderCube(object, matrix, buffer, argb, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                                      FaceDisplay.FRONT, camera, null);
                            }
                            matrix.popPose();
                        }
                    }
                }
            }
            profiler.pop();
        }
        return !replace;
    }

    private record ExtraBlockOutline(WorldRenderContext.BlockOutlineContext original, BlockPos blockPos, BlockState blockState,
                                     MultiBufferSource consumers) implements WorldRenderContext.BlockOutlineContext {
        @Override public VertexConsumer vertexConsumer() { return consumers.getBuffer(RenderType.lines()); }
        @Override public Entity entity() { return original.entity(); }
        @Override public double cameraX() { return original.cameraX(); }
        @Override public double cameraY() { return original.cameraY(); }
        @Override public double cameraZ() { return original.cameraZ(); }
    }

    public void renderQuadsWireFrame(BlockState state, VertexConsumer buffer, PoseStack matrix, RandomSource rand) {
        List<Line> lines = cachedWireFrames.get(state);
        if (lines == null) {
            BakedModel bakedModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            //TODO: Eventually we may want to add support for Model data and maybe render type
            lines = Outlines.extract(bakedModel, state, rand);
            cachedWireFrames.put(state, lines);
        }
        PoseStack.Pose pose = matrix.last();
        renderVertexWireFrame(lines, buffer, pose.pose(), pose.normal());
    }

    public static void renderVertexWireFrame(Collection<Line> lines, VertexConsumer buffer, Matrix4f pose, Matrix3f poseNormal) {
        //tmp variables to avoid allocating each loop
        Vector4f pos = new Vector4f();
        Vector3f normal = new Vector3f();
        renderVertexWireFrame(lines, buffer, pose, poseNormal, pos, normal);
    }

    public static void renderVertexWireFrame(Collection<Line> lines, VertexConsumer buffer, Matrix4f pose, Matrix3f poseNormal, Vector4f pos, Vector3f normal) {
        for (Line line : lines) {
            poseNormal.transform(line.nX(), line.nY(), line.nZ(), normal);

            pose.transform(line.x1(), line.y1(), line.z1(), 1F, pos);
            buffer.addVertex(pos.x, pos.y, pos.z)
                  .setColor(0, 0, 0, 102)
                  .setNormal(normal.x, normal.y, normal.z);

            pose.transform(line.x2(), line.y2(), line.z2(), 1F, pos);
            buffer.addVertex(pos.x, pos.y, pos.z)
                  .setColor(0, 0, 0, 102)
                  .setNormal(normal.x, normal.y, normal.z);
        }
    }

    private Model3D getOverlayModel(Direction side, TransmissionType type) {
        Map<TransmissionType, Model3D> modelMap = cachedOverlays.computeIfAbsent(side, s -> new EnumMap<>(TransmissionType.class));
        Model3D model = modelMap.get(type);
        if (model == null) {
            model = new Model3D().setTexture(MekanismRenderer.overlays.get(type))
                  .prepSingleFaceModelSize(side);
            modelMap.put(type, model);
        }
        return model;
    }

    public abstract static class LazyRender {

        public double distance;

        public abstract void render(Camera camera, VertexConsumer buffer, PoseStack poseStack, int renderTick, float partialTick, ProfilerFiller profiler);

        @NotNull
        public abstract Vec3 getCenterPos(float partialTick);

        @NotNull
        public abstract String getProfilerSection();

        @NotNull
        public abstract RenderType getRenderType();
    }

    private static void doTransparentRender(RenderType renderType, LazyRender transparentRender, Camera camera, BufferSource renderer, PoseStack poseStack, int renderTick, float partialTick, ProfilerFiller profiler) {
        //Batch all renders for a single render type into a single buffer addition
        VertexConsumer buffer = renderer.getBuffer(renderType);

        String profilerSection = transparentRender.getProfilerSection();
        profiler.push(profilerSection);
        transparentRender.render(camera, buffer, poseStack, renderTick, partialTick, profiler);
        profiler.pop();
    }
}
