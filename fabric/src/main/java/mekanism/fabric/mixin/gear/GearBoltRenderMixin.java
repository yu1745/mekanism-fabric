package mekanism.fabric.mixin.gear;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import mekanism.fabric.client.render.FabricBoltRendering;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class GearBoltRenderMixin {
    //Both native graphics branches, before Fabulous clears PARTICLES_TARGET. PoseStack is the actual sole local (slot 24).
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
          "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
          shift = At.Shift.AFTER), require = 2)
    private void mekanism$afterParticles(DeltaTracker delta, boolean outline, Camera camera, GameRenderer renderer,
          LightTexture light, Matrix4f frustum, Matrix4f projection, CallbackInfo ci, @Local PoseStack pose) {
        FabricBoltRendering.afterParticles(delta.getGameTimeDeltaPartialTick(false), pose, camera);
    }
}
