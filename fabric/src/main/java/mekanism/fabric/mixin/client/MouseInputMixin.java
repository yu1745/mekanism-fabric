package mekanism.fabric.mixin.client;

import mekanism.client.key.MekanismKeyHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseInputMixin {

    // Run after vanilla normalizes macOS control-click and filters duplicate touchscreen events.
    @Inject(method = "onPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0))
    private void mekanism$onButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow()) {
            MekanismKeyHandler.onInput(button, 0, action, true);
        }
    }
}
