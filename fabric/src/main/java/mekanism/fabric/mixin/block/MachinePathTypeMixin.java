package mekanism.fabric.mixin.block;

import mekanism.common.block.prefab.BlockBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pinned patched WalkNodeEvaluator asks for the machine override before its normal state classification. */
@Mixin(WalkNodeEvaluator.class)
abstract class MachinePathTypeMixin {
    @Inject(method = "getPathTypeFromState", at = @At("HEAD"), cancellable = true)
    private static void mekanism$machinePathType(BlockGetter level, BlockPos pos, CallbackInfoReturnable<PathType> cir) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockBase<?> machine) {
            PathType type = machine.getBlockPathType(state, level, pos, null);
            if (type != null) {
                cir.setReturnValue(type);
            }
        }
    }
}
