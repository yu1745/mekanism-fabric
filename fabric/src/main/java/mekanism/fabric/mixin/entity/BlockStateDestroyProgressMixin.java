package mekanism.fabric.mixin.entity;

import mekanism.common.CommonPlayerTickHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric port delta: the NeoForge break-speed event fires inside the mining-speed query with an optional block position; vanilla's
 * destroy-progress query is linear in that speed and always has the position, so the project's multiplicative adjustments are
 * applied to the returned progress instead.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateDestroyProgressMixin {

    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void mekanism$modifyDestroyProgress(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        float progress = cir.getReturnValue();
        if (progress > 0) {
            cir.setReturnValue(CommonPlayerTickHandler.modifyBreakProgress(player,
                  ((BlockBehaviour.BlockStateBase) (Object) this).asState(), pos, progress));
        }
    }
}
