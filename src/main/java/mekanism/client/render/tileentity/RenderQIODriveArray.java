package mekanism.client.render.tileentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mekanism.client.model.MekanismModelCache;
import mekanism.client.render.lib.Quad;
import mekanism.client.render.lib.QuadTransformation;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.tile.qio.TileEntityQIODriveArray;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port of the official DriveArrayBakedModel's drive layer: the official wrapper read the per-drive states
 * from model data and baked translated drive quads into the block model; on this port the tile's render data (the
 * same byte[] the official model-data property carried) drives a tile renderer that draws the drive quads additively
 * over the static array shell - the layering precedent of the energy cube's LED half.
 */
public class RenderQIODriveArray implements BlockEntityRenderer<TileEntityQIODriveArray> {

    //The official DRIVE_PLACEMENTS: column, then row - offsets along the block's local X, and up the front face.
    private static final float[][] DRIVE_PLACEMENTS = {
          {0, 6F / 16}, {-2F / 16, 6F / 16}, {-4F / 16, 6F / 16}, {-7F / 16, 6F / 16}, {-9F / 16, 6F / 16}, {-11F / 16, 6F / 16},
          {0, 0}, {-2F / 16, 0}, {-4F / 16, 0}, {-7F / 16, 0}, {-9F / 16, 0}, {-11F / 16, 0}
    };

    public RenderQIODriveArray(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull TileEntityQIODriveArray tile, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer,
          int packedLight, int packedOverlay) {
        if (tile.getLevel() == null || !tile.hasLevel()) {
            return;
        }
        byte[] driveStatus = tile.getRenderData();
        if (driveStatus == null || Arrays.equals(driveStatus, new byte[driveStatus.length])) {
            return;
        }
        BlockState blockState = tile.getBlockState();
        Direction facing = Attribute.getFacing(blockState);
        if (facing == null) {
            return;
        }
        QuadTransformation rotation = QuadTransformation.rotate(facing);
        List<BakedQuad> driveQuads = new ArrayList<>();
        //The official createQuads body: translate each drive's quads into its bay, rotated to the block's facing.
        for (int i = 0; i < driveStatus.length; i++) {
            TileEntityQIODriveArray.DriveStatus status = TileEntityQIODriveArray.DriveStatus.BY_ID.apply(driveStatus[i]);
            if (status == TileEntityQIODriveArray.DriveStatus.NONE) {
                continue;
            }
            float[] translation = DRIVE_PLACEMENTS[i];
            QuadTransformation transformation = QuadTransformation.translate(translation[0], translation[1], 0);
            //Side is always null in the official key (the drive faces don't cull).
            List<BakedQuad> sourceQuads = MekanismModelCache.INSTANCE.QIO_DRIVES[status.ordinal()].getQuads(blockState, null, RandomSource.create());
            for (BakedQuad bakedQuad : sourceQuads) {
                Quad quad = new Quad(bakedQuad);
                if (quad.transform(transformation, rotation)) {
                    driveQuads.add(quad.bake());
                } else {
                    driveQuads.add(bakedQuad);
                }
            }
        }
        if (driveQuads.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        PoseStack.Pose pose = poseStack.last();
        for (BakedQuad quad : driveQuads) {
            consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }
}
