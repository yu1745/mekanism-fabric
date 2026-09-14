package mekanism.fabric.mixin.block;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mekanism.common.block.prefab.BlockTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Retains the original machine's all-face emitter connection, including vertical dust steps. */
@Mixin(RedStoneWireBlock.class)
abstract class MachineRedstoneConnectionMixin {
    @WrapOperation(method = "getConnectingSide(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/level/block/state/properties/RedstoneSide;",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;shouldConnectTo(Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 0))
    private boolean mekanism$above(BlockState state, Operation<Boolean> original, BlockGetter level, BlockPos pos, Direction direction, boolean aboveOpen) {
        return state.getBlock() instanceof BlockTile<?, ?> tile
              ? tile.canConnectRedstone(state, level, pos.relative(direction).above(), null) : original.call(state);
    }

    @WrapOperation(method = "getConnectingSide(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/level/block/state/properties/RedstoneSide;",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;shouldConnectTo(Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 1))
    private boolean mekanism$below(BlockState state, Operation<Boolean> original, BlockGetter level, BlockPos pos, Direction direction, boolean aboveOpen) {
        return state.getBlock() instanceof BlockTile<?, ?> tile
              ? tile.canConnectRedstone(state, level, pos.relative(direction).below(), null) : original.call(state);
    }

    @WrapOperation(method = "getConnectingSide(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/level/block/state/properties/RedstoneSide;",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;shouldConnectTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"))
    private boolean mekanism$side(BlockState state, Direction side, Operation<Boolean> original,
          BlockGetter level, BlockPos pos, Direction direction, boolean aboveOpen) {
        return state.getBlock() instanceof BlockTile<?, ?> tile
              ? tile.canConnectRedstone(state, level, pos.relative(direction), side) : original.call(state, side);
    }
}
