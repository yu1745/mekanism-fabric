package mekanism.fabric.mixin.client;

import mekanism.client.ClientTickHandler;
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
    private static void mekanism$fogOverride(CallbackInfo ci) {
        float end = ClientTickHandler.onFogEnd(com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd());
        if (end != com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd()) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(ClientTickHandler.onFogStart(com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart()));
            com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(end);
        }
    }
}
