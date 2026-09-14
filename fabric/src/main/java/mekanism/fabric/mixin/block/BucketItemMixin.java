package mekanism.fabric.mixin.block;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import mekanism.common.block.states.IStateFluidLoggable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Preserves Mekanism fluid-logging at vanilla's two water-only bucket decisions. */
@Mixin(BucketItem.class)
abstract class BucketItemMixin {

    @Shadow @Final private Fluid content;

    @ModifyExpressionValue(method = "use", at = @At(value = "FIELD",
          target = "Lnet/minecraft/world/level/material/Fluids;WATER:Lnet/minecraft/world/level/material/FlowingFluid;"))
    private FlowingFluid mekanism$selectLoggedPosition(FlowingFluid water, @Local BlockState state,
          @Local BlockHitResult hit, @Local(argsOnly = true) Level level, @Local(argsOnly = true) Player player) {
        return content instanceof FlowingFluid flowing && state.getBlock() instanceof IStateFluidLoggable loggable
              && loggable.canPlaceLiquid(player, level, hit.getBlockPos(), state, content) ? flowing : water;
    }

    @ModifyExpressionValue(method = "emptyContents", at = @At(value = "FIELD",
          target = "Lnet/minecraft/world/level/material/Fluids;WATER:Lnet/minecraft/world/level/material/FlowingFluid;"))
    private FlowingFluid mekanism$fillLoggedBlock(FlowingFluid water, @Local BlockState state,
          @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) Level level, @Local(argsOnly = true) Player player) {
        // This decision remains after vanilla's ultra-warm evaporation branch.
        return content instanceof FlowingFluid flowing && state.getBlock() instanceof IStateFluidLoggable loggable
              && loggable.canPlaceLiquid(player, level, pos, state, content) ? flowing : water;
    }

    @WrapOperation(method = "use", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/BucketPickup;getPickupSound()Ljava/util/Optional;"))
    private Optional<SoundEvent> mekanism$pickupSound(BucketPickup pickup, Operation<Optional<SoundEvent>> original,
          @Local BlockState state) {
        // The local is the pre-pickup state; looking it up in the world now would observe the empty block.
        return pickup instanceof IStateFluidLoggable loggable ? loggable.getPickupSound(state) : original.call(pickup);
    }
}
