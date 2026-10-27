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

    // Match NeoForge's in-world scroll hook: after window/overlay/screen checks and vanilla accumulation,
    // immediately before spectator or hotbar handling. The event receives the adjusted, not raw, Y delta.
    @Inject(method = "onScroll(JDD)V", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"), cancellable = true)
    private void mekanism$modeScroll(long window, double scrollX, double scrollY, CallbackInfo ci) {
        var options = net.minecraft.client.Minecraft.getInstance().options;
        double adjustedY = (options.discreteMouseScroll().get() ? Math.signum(scrollY) : scrollY) * options.mouseWheelSensitivity().get();
        if (ClientTickHandler.INSTANCE.onMouseEvent(adjustedY)) {
            ci.cancel();
        }
    }
}
