package mekanism.fabric.mixin.client;

import mekanism.client.render.RenderPropertiesProvider;
import mekanism.common.block.BlockBounding;
import mekanism.common.block.BlockMekanism;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native entrypoints for the official Mekanism client block extensions. */
@Mixin(ParticleEngine.class)
abstract class BlockParticlesMixin {
    @Shadow private ClientLevel level;

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void mekanism$destroyParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!state.isAir() && state.getBlock() instanceof BlockMekanism) {
            RenderPropertiesProvider.addDestroyEffects(state, level, pos, (ParticleEngine) (Object) this);
            ci.cancel();
        }
    }

    @Inject(method = "crack", at = @At("HEAD"), cancellable = true)
    private void mekanism$boundingHitParticles(BlockPos pos, Direction side, CallbackInfo ci) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockBounding &&
              RenderPropertiesProvider.addBoundingHitEffects(state, level, pos, side, (ParticleEngine) (Object) this)) {
            ci.cancel();
        }
    }
}
