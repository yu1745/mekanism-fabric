package mekanism.fabric.mixin.block;

import mekanism.common.block.basic.BlockResource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Extends vanilla portal-frame checks with the official Mekanism resource-block property. */
@Mixin(PortalShape.class)
abstract class PortalFrameMixin {
    @Redirect(method = {"getDistanceUntilEdgeAboveFrame", "hasTopFrame", "getDistanceUntilTop"}, at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$StatePredicate;test(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean mekanism$resourcePortalFrame(BlockBehaviour.StatePredicate original, BlockState state, BlockGetter world, BlockPos pos) {
        return original.test(state, world, pos) || state.getBlock() instanceof BlockResource resource && resource.getResourceInfo().isPortalFrame();
    }
}
