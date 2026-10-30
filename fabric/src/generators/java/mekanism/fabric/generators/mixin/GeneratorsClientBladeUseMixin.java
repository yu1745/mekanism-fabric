package mekanism.fabric.generators.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mekanism.generators.common.item.ItemTurbineBlade;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPlayerGameMode.class)
abstract class GeneratorsClientBladeUseMixin {
    @ModifyExpressionValue(method = "performUseItemOn", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/player/LocalPlayer;isSecondaryUseActive()Z"))
    private boolean mekanismgenerators$bladeUse(boolean secondary, LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        var stack = player.getItemInHand(hand);
        return secondary && !(stack.getItem() instanceof ItemTurbineBlade blade
              && blade.doesSneakBypassUse(stack, player.level(), hit.getBlockPos(), player));
    }
}
