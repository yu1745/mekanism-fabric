package mekanism.fabric.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import mekanism.fabric.client.model.FabricSeparateTransformsModel;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Select the Mekanism perspective before vanilla applies its transforms and renders its quads. */
@Mixin(ItemRenderer.class)
abstract class SeparateTransformsItemRendererMixin {
    @ModifyVariable(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
          at = @At("HEAD"), argsOnly = true)
    private BakedModel mekanism$selectPerspective(BakedModel model, @Local(argsOnly = true) ItemDisplayContext context) {
        return FabricSeparateTransformsModel.forContext(model, context);
    }
}
