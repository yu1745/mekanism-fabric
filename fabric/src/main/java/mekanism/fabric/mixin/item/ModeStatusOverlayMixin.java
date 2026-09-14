package mekanism.fabric.mixin.item;

import mekanism.client.render.hud.MekanismStatusOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original status overlay ordering: immediately above the selected-item-name layer, not a second HUD framework. */
@Mixin(Gui.class)
abstract class ModeStatusOverlayMixin {
    @Inject(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/gui/Gui;renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V", shift = At.Shift.AFTER))
    private void mekanism$renderModeStatus(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        MekanismStatusOverlay.INSTANCE.render(graphics, delta);
    }
}
