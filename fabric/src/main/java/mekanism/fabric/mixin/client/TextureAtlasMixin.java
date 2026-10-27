package mekanism.fabric.mixin.client;

import mekanism.client.render.MekanismRenderer;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric port of NeoForge's TextureAtlasStitchedEvent: fire the renderer sprite-refresh hook right after the
 * atlas uploads its stitched sprites, the same point in the resource-load flow the official event fired at.
 */
@Mixin(TextureAtlas.class)
abstract class TextureAtlasMixin {

    @Shadow
    @Final
    private ResourceLocation location;

    @Inject(method = "upload", at = @At("RETURN"))
    private void mekanism$afterStitch(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        if (TextureAtlas.LOCATION_BLOCKS.equals(this.location)) {
            MekanismRenderer.onStitch((TextureAtlas) (Object) this);
            mekanism.common.base.holiday.ClientHolidayInfo.onStitch((TextureAtlas) (Object) this);
        }
    }
}
