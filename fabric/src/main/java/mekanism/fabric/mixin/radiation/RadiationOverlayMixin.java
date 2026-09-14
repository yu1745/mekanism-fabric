package mekanism.fabric.mixin.radiation;

import mekanism.client.render.hud.RadiationOverlay;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.LayeredDraw;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native below-all layer, retaining native depth separation and the original unconditional layer visibility. */
@Mixin(Gui.class)
abstract class RadiationOverlayMixin {
    @Shadow @Final private LayeredDraw layers;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mekanism$prependRadiationOverlay(CallbackInfo ci) {
        layers.layers.addFirst(RadiationOverlay.INSTANCE);
    }
}
