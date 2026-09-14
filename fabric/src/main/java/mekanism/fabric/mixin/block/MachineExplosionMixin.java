package mekanism.fabric.mixin.block;

import java.util.function.BiConsumer;
import mekanism.common.block.BlockMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only vanilla's removal tail, after its normal guards and loot handling. */
@Mixin(BlockBehaviour.class)
abstract class MachineExplosionMixin {

    @Inject(method = "onExplosionHit", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"), cancellable = true)
    private void mekanism$beforeMachineRemoval(BlockState state, Level level, BlockPos pos, Explosion explosion,
          BiConsumer<ItemStack, BlockPos> drops, CallbackInfo ci) {
        if (state.getBlock() instanceof BlockMekanism machine) {
            machine.onBlockExploded(state, level, pos, explosion);
            ci.cancel();
        }
    }
}
