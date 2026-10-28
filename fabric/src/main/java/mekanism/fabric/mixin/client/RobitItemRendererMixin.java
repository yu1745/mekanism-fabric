package mekanism.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import mekanism.common.item.ItemRobit;
import mekanism.fabric.client.render.item.FabricItemRendererRegistration;
import mekanism.fabric.client.render.item.FabricRobitItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve the model already selected with the real item override world, entity and seed. */
@Mixin(ItemRenderer.class)
public abstract class RobitItemRendererMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BlockEntityWithoutLevelRenderer;renderByItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private void mekanism$renderResolvedRobit(BlockEntityWithoutLevelRenderer renderer, ItemStack stack, ItemDisplayContext context,
          PoseStack matrices, MultiBufferSource buffers, int light, int overlay, Operation<Void> original,
          @Local(argsOnly = true) BakedModel selectedModel) {
        if (stack.getItem() instanceof ItemRobit) {
            BakedModel geometry = FabricItemRendererRegistration.robitGeometry(selectedModel);
            if (geometry != null) {
                FabricRobitItemRenderer.renderResolved(stack, matrices, buffers, light, overlay, geometry);
                return;
            }
        }
        original.call(renderer, stack, context, matrices, buffers, light, overlay);
    }
}
