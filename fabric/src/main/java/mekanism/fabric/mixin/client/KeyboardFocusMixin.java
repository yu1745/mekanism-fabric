package mekanism.fabric.mixin.client;

import mekanism.client.key.MekKeyHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class KeyboardFocusMixin {
    @Inject(method = "setWindowActive", at = @At("HEAD"))
    private void mekanism$clearScancodesOnFocusLoss(boolean active, CallbackInfo ci) {
        if (!active) {
            MekKeyHandler.clearScanCodes();
        }
    }
}
