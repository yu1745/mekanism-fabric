package mekanism.fabric.mixin.client;

import mekanism.client.ClientTickHandler;
import mekanism.fabric.client.FabricFluidColors;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.FastColor;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric port of the official {@code ViewportEvent.ComputeFogColor} handler: the vision-enhancement module tint folds
 * into the fluid-adjusted fog color fields before vanilla clears and applies the final color.
 */
@Mixin(FogRenderer.class)
abstract class FogColorMixin {

    @Inject(method = "setupColor", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V", ordinal = 1))
    private static void mekanism$fogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, CallbackInfo ci) {
        var type = FabricFluidColors.getSubmergedType(level, camera.getPosition());
        if (type != null) {
            FogRenderer.fogRed = FastColor.ARGB32.red(type.color) / 255F;
            FogRenderer.fogGreen = FastColor.ARGB32.green(type.color) / 255F;
            FogRenderer.fogBlue = FastColor.ARGB32.blue(type.color) / 255F;
        }
        //The original fluid extension runs before ComputeFogColor, and both precede clearColor.
        ClientTickHandler.onFogLighting();
    }
}
