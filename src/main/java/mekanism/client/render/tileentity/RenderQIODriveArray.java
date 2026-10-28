package mekanism.client.render.tileentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.tile.qio.TileEntityQIODriveArray;
import mekanism.fabric.client.model.FabricQIODriveGeometry;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;

/** Renders the official drive geometry over the static QIO array shell. */
public class RenderQIODriveArray implements BlockEntityRenderer<TileEntityQIODriveArray> {

    public RenderQIODriveArray(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull TileEntityQIODriveArray tile, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer,
          int packedLight, int packedOverlay) {
        if (!tile.hasLevel() || Attribute.getFacing(tile.getBlockState()) == null) {
            return;
        }
        VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        FabricQIODriveGeometry.forEach(tile.getRenderData(), tile.getBlockState(), RandomSource.create(), (quad, minimumLight) -> {
            int light = LightTexture.pack(Math.max(LightTexture.block(packedLight), LightTexture.block(minimumLight)),
                  Math.max(LightTexture.sky(packedLight), LightTexture.sky(minimumLight)));
            consumer.putBulkData(poseStack.last(), quad, 1F, 1F, 1F, 1F, light, packedOverlay);
        });
    }
}
