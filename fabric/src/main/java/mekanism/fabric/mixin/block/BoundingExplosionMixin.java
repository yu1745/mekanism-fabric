package mekanism.fabric.mixin.block;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mekanism.common.block.BlockBounding;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Resolve bounding resistance at the original master position; vanilla still combines it with the fluid resistance. */
@Mixin(ExplosionDamageCalculator.class)
abstract class BoundingExplosionMixin {

    @WrapOperation(method = "getBlockExplosionResistance", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/Block;getExplosionResistance()F"))
    private float mekanism$masterResistance(Block block, Operation<Float> original,
          @Local(argsOnly = true) Explosion explosion, @Local(argsOnly = true) BlockGetter level,
          @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) BlockState state) {
        return block instanceof BlockBounding bounding ? bounding.getExplosionResistance(state, level, pos, explosion) : original.call(block);
    }
}
