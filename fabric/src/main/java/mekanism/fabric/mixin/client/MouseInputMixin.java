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

    @Inject(method = "onPress", at = @At("HEAD"))
    private void mekanism$onButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow()) {
            MekanismKeyHandler.onInput(button, 0, action, true);
        }
    }
}
