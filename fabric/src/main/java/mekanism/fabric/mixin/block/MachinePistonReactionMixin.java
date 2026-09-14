package mekanism.fabric.mixin.block;

import mekanism.common.block.BlockMekanism;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Protect the actual machine tile; retain the native property's reaction when the contextual callback returns null. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class MachinePistonReactionMixin {

    @Inject(method = "getPistonPushReaction", at = @At("HEAD"), cancellable = true)
    private void mekanism$machineReaction(CallbackInfoReturnable<PushReaction> cir) {
        BlockState state = (BlockState) (Object) this;
        if (state.getBlock() instanceof BlockMekanism machine) {
            PushReaction reaction = machine.getPistonPushReaction(state);
            if (reaction != null) {
                cir.setReturnValue(reaction);
            }
        }
    }
}
