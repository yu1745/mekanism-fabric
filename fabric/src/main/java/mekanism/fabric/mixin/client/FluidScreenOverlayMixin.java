package mekanism.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import mekanism.fabric.client.FabricFluidColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Reuses vanilla's identical underwater drawing with the original Mekanism fluid overlay texture. */
@Mixin(ScreenEffectRenderer.class)
abstract class FluidScreenOverlayMixin {
    @WrapOperation(method = "renderScreenEffect", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/player/LocalPlayer;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private static boolean mekanism$fluidOverlay(LocalPlayer player, TagKey<Fluid> tag, Operation<Boolean> original,
          @Local(argsOnly = true) Minecraft minecraft, @Local(argsOnly = true) PoseStack poses) {
        boolean water = original.call(player, tag);
        if (!water) {
            var type = FabricFluidColors.getEyeType(player);
            if (type != null && type.renderOverlayTexture != null) {
                ScreenEffectRenderer.renderWater(minecraft, poses);
            }
        }
        return water;
    }

    @WrapOperation(method = "renderWater", at = @At(value = "INVOKE",
          target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"))
    private static void mekanism$overlayTexture(int slot, ResourceLocation texture, Operation<Void> original,
          @Local(argsOnly = true) Minecraft minecraft) {
        if (!minecraft.player.isEyeInFluid(FluidTags.WATER)) {
            var type = FabricFluidColors.getEyeType(minecraft.player);
            if (type != null && type.renderOverlayTexture != null) {
                texture = type.renderOverlayTexture;
            }
        }
        original.call(slot, texture);
    }
}
