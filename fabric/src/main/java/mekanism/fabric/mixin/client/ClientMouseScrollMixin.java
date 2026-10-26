package mekanism.fabric.mixin.client;

import mekanism.client.ClientTickHandler;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric port of the official {@code InputEvent.MouseScrollingEvent} handler: shift-scrolling a mode item turns the
 * scroll into a mode change and cancels vanilla scrolling (the vanilla hotbar selection must not move).
 */
@Mixin(MouseHandler.class)
abstract class ClientMouseScrollMixin {

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void mekanism$modeScroll(long window, double scrollX, double scrollY, CallbackInfo ci) {
        if (ClientTickHandler.INSTANCE.onMouseEvent(scrollY)) {
            ci.cancel();
        }
    }
}
