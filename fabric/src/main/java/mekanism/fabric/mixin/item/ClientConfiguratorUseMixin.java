package mekanism.fabric.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mekanism.common.item.ItemConfigurator;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Match the server decision without changing the real player sneaking state or item-use/permission flow. */
@Mixin(MultiPlayerGameMode.class)
abstract class ClientConfiguratorUseMixin {
    @ModifyExpressionValue(method = "performUseItemOn", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/player/LocalPlayer;isSecondaryUseActive()Z"))
    private boolean mekanism$configuratorBlockUse(boolean secondary, LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        return secondary && !ItemConfigurator.doesSneakBypassUse(player, hit.getBlockPos());
    }
}
