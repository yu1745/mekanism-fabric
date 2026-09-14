package mekanism.fabric.mixin.block;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mekanism.common.block.BlockBounding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Retains the actual bounding/master tiles until the native harvesting call has copied their contents. */
@Mixin(ServerPlayerGameMode.class)
abstract class BoundingDestroyMixin {

    @Shadow protected ServerPlayer player;
    @Shadow public abstract boolean isCreative();

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean mekanism$deferBoundingHarvest(ServerLevel level, BlockPos pos, boolean moving, Operation<Boolean> original,
          @Local(index = 4) BlockState state) {
        if (state.getBlock() instanceof BlockBounding bounding) {
            return bounding.onDestroyedByPlayer(state, level, pos, player,
                  !isCreative() && player.hasCorrectToolForDrops(state), level.getFluidState(pos));
        }
        return original.call(level, pos, moving);
    }
}
