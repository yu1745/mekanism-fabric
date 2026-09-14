package mekanism.fabric.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mekanism.common.item.ItemConfigurator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only the configurator's original wrench-mode sneak bypass; no general item-extension emulation. */
@Mixin(ServerPlayerGameMode.class)
abstract class ConfiguratorUseMixin {
    @ModifyExpressionValue(method = "useItemOn", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerPlayer;isSecondaryUseActive()Z"))
    private boolean mekanism$configuratorBlockUse(boolean secondary, ServerPlayer player, Level level, ItemStack stack,
          InteractionHand hand, BlockHitResult hit) {
        return secondary && !ItemConfigurator.doesSneakBypassUse(player, hit.getBlockPos());
    }
}
