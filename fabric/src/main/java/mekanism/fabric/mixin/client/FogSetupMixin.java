package mekanism.fabric.mixin.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import mekanism.client.ClientTickHandler;
import mekanism.fabric.client.FabricFluidColors;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric port of the official cancellable {@code ViewportEvent.RenderFog} handler: with the vision-enhancement module
 * enabled the near plane drops to the spectator lava value and the far plane takes the official transform plus the
 * per-module distance scaling. Re-issuing the shader fog values after vanilla's application replaces them - the same
 * end state the event's cancellation produced.
 */
@Mixin(FogRenderer.class)
abstract class FogSetupMixin {

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void mekanism$fogOverride(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thickFog, float partialTick, CallbackInfo ci) {
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        FogShape shape = RenderSystem.getShaderFogShape();
        if (FabricFluidColors.getSubmergedType(camera.getEntity().level(), camera.getPosition()) != null) {
            RenderSystem.setShaderFogStart(-8);
            RenderSystem.setShaderFogEnd(Math.min(24F, renderDistance));
            if (24F > renderDistance) {
                RenderSystem.setShaderFogShape(FogShape.CYLINDER);
            }
        }
        //RenderFog receives the original vanilla values after the fluid extension has run.
        //Cancellation overrides all three values, including values numerically equal to vanilla's.
        if (ClientTickHandler.overridesFog()) {
            RenderSystem.setShaderFogStart(ClientTickHandler.onFogStart(start));
            RenderSystem.setShaderFogEnd(ClientTickHandler.onFogEnd(end));
            RenderSystem.setShaderFogShape(shape);
        }
    }
}
