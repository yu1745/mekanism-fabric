package mekanism.fabric.mixin.client;

import mekanism.client.key.MekanismKeyHandler;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardInputMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void mekanism$onKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow()) {
            MekanismKeyHandler.onInput(key, scanCode, action, false);
        }
    }
}
