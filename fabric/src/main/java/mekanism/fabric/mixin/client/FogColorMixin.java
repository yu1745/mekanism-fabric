package mekanism.fabric.mixin.client;

import mekanism.client.ClientTickHandler;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric port of the official {@code ViewportEvent.ComputeFogColor} handler: the vision-enhancement module tint folds
 * into the fog color fields after vanilla computes them and before levelFogColor applies them to the shader.
 */
@Mixin(FogRenderer.class)
abstract class FogColorMixin {

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void mekanism$fogColor(CallbackInfo ci) {
        ClientTickHandler.onFogLighting();
    }
}
