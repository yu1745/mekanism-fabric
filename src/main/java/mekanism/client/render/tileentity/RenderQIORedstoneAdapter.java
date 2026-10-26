package mekanism.client.render.tileentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import mekanism.api.MekanismAPI;

import mekanism.common.tile.qio.TileEntityQIORedstoneAdapter;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric port replacement for the official {@code mekanism:data_based} model switching: the adapter's lit/unlit
 * switch rides the tile's render data instead of NeoForge model data, so the static bake is the unlit model and this
 * renderer draws the lit model's quads additively over the emitting faces when the tile reports it is emitting (the
 * same layering precedent as the energy cube's active LED half).
 */
public class RenderQIORedstoneAdapter implements BlockEntityRenderer<TileEntityQIORedstoneAdapter> {

    public RenderQIORedstoneAdapter(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull TileEntityQIORedstoneAdapter tile, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer,
          int packedLight, int packedOverlay) {
        Boolean renderData = tile.getRenderData();
        if (tile.getLevel() == null || !tile.hasLevel() || renderData == null || !renderData) {
            return;
        }
        BakedModel litModel = mekanism.fabric.client.model.FabricDataBasedModels.getLitModel(
              ResourceLocation.fromNamespaceAndPath(MekanismAPI.MEKANISM_MODID, "block/qio_redstone_adapter_lit"));
        if (litModel == null) {
            return;
        }
        poseStack.pushPose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.solid());
        List<BakedQuad> quads = litModel.getQuads(null, null, RandomSource.create());
        PoseStack.Pose pose = poseStack.last();
        for (BakedQuad quad : quads) {
            consumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }
}
