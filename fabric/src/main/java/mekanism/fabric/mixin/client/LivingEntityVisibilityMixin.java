package mekanism.fabric.mixin.client;

import mekanism.client.ClientTickHandler;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric port of the official {@code RenderLivingEvent.Pre/Post} handlers: MekaSuit wearers (and HDPE elytra capes)
 * hide the vanilla humanoid model parts for the duration of the render, restored afterwards so other entities of the
 * same renderer stay visible.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityVisibilityMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void mekanism$preRender(LivingEntity entity, float entityYaw, float partialTick,
          com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        ClientTickHandler.onRenderEntityPre(entity, ((LivingEntityRenderer<?, ?>) (Object) this).getModel());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mekanism$postRender(LivingEntity entity, float entityYaw, float partialTick,
          com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        ClientTickHandler.onRenderEntityPost(entity, ((LivingEntityRenderer<?, ?>) (Object) this).getModel());
    }
}
