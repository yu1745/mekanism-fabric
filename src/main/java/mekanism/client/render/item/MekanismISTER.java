package mekanism.client.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mekanism.common.util.EnumUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public abstract class MekanismISTER extends BlockEntityWithoutLevelRenderer {

    protected MekanismISTER() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    protected EntityModelSet getEntityModels() {
        //Just have this method as a helper for what we pass as entity models rather than bothering to
        // use an AT to access it directly
        return Minecraft.getInstance().getEntityModels();
    }

    protected BlockEntityRenderDispatcher getBlockEntityRenderDispatcher() {
        //Just have this method as a helper for what we pass as the block entity render dispatcher
        // rather than bothering to use an AT to access it directly
        return Minecraft.getInstance().getBlockEntityRenderDispatcher();
    }

    protected Camera getCamera() {
        return getBlockEntityRenderDispatcher().camera;
    }

    @Override
    public abstract void onResourceManagerReload(@NotNull ResourceManager resourceManager);

    @Override
    public abstract void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext displayContext, @NotNull PoseStack matrix, @NotNull MultiBufferSource renderer,
          int light, int overlayLight);

    /**
     * @implNote Heavily based on/from vanilla's ItemRenderer#render code that calls the renderByItem method on the ISBER
     */
    protected void renderBlockItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext displayContext, @NotNull PoseStack matrix, @NotNull MultiBufferSource renderer,
          int light, int overlayLight) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        Block block = blockItem.getBlock();
        boolean fabulous;
        if (displayContext != ItemDisplayContext.GUI && !displayContext.firstPerson()) {
            fabulous = !(block instanceof HalfTransparentBlock) && !(block instanceof StainedGlassPaneBlock);
        } else {
            fabulous = true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BlockState defaultState = block.defaultBlockState();
        //TODO: See if we can come up with a better way to handle getting the model, maybe even one that supports non block items??
        BakedModel baseModel = minecraft.getModelManager().getBlockModelShaper().getBlockModel(defaultState);
        //Vanilla 1.21.1 renders block items from the single block model with the state's render type; the NeoForge
        //render-pass/model-data hooks this method was written against have no vanilla counterpart.
        RenderType renderType = ItemBlockRenderTypes.getRenderType(defaultState, fabulous);
        VertexConsumer buffer;
        if (fabulous) {
            buffer = ItemRenderer.getFoilBufferDirect(renderer, renderType, true, stack.hasFoil());
        } else {
            buffer = ItemRenderer.getFoilBuffer(renderer, renderType, true, stack.hasFoil());
        }
        PoseStack.Pose pose = matrix.last();
        for (Direction direction : EnumUtils.DIRECTIONS) {
            RandomSource random = RandomSource.create(42L);
            for (BakedQuad quad : baseModel.getQuads(defaultState, direction, random)) {
                renderQuad(minecraft, buffer, pose, quad, stack, light, overlayLight);
            }
        }
        RandomSource random = RandomSource.create(42L);
        for (BakedQuad quad : baseModel.getQuads(defaultState, null, random)) {
            renderQuad(minecraft, buffer, pose, quad, stack, light, overlayLight);
        }
    }

    //Vanilla ItemRenderer#renderQuadList inlined: it is private in 1.21.1 and NeoForge's widened form is gone.
    private static void renderQuad(Minecraft minecraft, VertexConsumer buffer, PoseStack.Pose pose, BakedQuad quad, ItemStack stack, int light, int overlayLight) {
        int color = -1;
        if (!stack.isEmpty() && quad.isTinted()) {
            color = minecraft.itemColors.getColor(stack, quad.getTintIndex());
        }
        float alpha = FastColor.ARGB32.alpha(color) / 255.0F;
        float red = FastColor.ARGB32.red(color) / 255.0F;
        float green = FastColor.ARGB32.green(color) / 255.0F;
        float blue = FastColor.ARGB32.blue(color) / 255.0F;
        buffer.putBulkData(pose, quad, red, green, blue, alpha, light, overlayLight);
    }
}