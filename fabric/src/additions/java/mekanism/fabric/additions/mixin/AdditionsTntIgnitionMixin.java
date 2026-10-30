package mekanism.fabric.additions.mixin;

import mekanism.additions.common.block.BlockObsidianTNT;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native TNT callers keep their block removal, sound and player/item paths; Additions owns its primed entity. */
@Mixin(TntBlock.class)
abstract class AdditionsTntIgnitionMixin {
    @Inject(method = "explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)V",
          at = @At("HEAD"), cancellable = true)
    private static void mekanismadditions$prime(Level level, BlockPos pos, LivingEntity igniter, CallbackInfo ci) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockObsidianTNT tnt) {
            tnt.onCaughtFire(state, level, pos, null, igniter);
            ci.cancel();
        }
    }
}
