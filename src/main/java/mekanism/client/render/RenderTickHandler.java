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
import mekanism.client.render.MekanismRenderer.Model3D;
import mekanism.client.render.lib.Outlines;
import mekanism.client.render.lib.Outlines.Line;
import mekanism.fabric.client.render.FabricBoltRendering;
import mekanism.common.base.ProfilerConstants;
import mekanism.common.lib.effect.BoltEffect;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
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
 * renderers, and the configurator overlay model cache. The arm/HUD/highlight/screen/tick listeners of the official
 * file stay deferred with their own lanes (armor renderers, HUD, item extensions, ClientTickHandler) — see
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
