package mekanism.fabric.generators.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mekanism.generators.common.item.ItemTurbineBlade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerGameMode.class)
abstract class GeneratorsBladeUseMixin {
    @ModifyExpressionValue(method = "useItemOn", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerPlayer;isSecondaryUseActive()Z"))
    private boolean mekanismgenerators$bladeUse(boolean secondary, ServerPlayer player, Level level, ItemStack stack,
          InteractionHand hand, BlockHitResult hit) {
        return secondary && !(stack.getItem() instanceof ItemTurbineBlade blade
              && blade.doesSneakBypassUse(stack, level, hit.getBlockPos(), player));
    }
}
