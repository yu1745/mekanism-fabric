package mekanism.fabric.mixin.server;

import mekanism.fabric.item.FabricHopperTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge routes vanilla hoppers through the machine's item capability; vanilla has no such hook, so the two
 * transfer entry points consult the Mekanism-specific bridge first. The bridge only claims Mekanism-owned storage
 * views, and only when it actually moved an item - otherwise vanilla behavior proceeds untouched.
 */
@Mixin(HopperBlockEntity.class)
abstract class HopperTransferMixin {

    @Inject(method = "ejectItems", at = @At("HEAD"), cancellable = true)
    private static void mekanism$ejectToMachine(Level level, BlockPos pos, HopperBlockEntity hopper,
          CallbackInfoReturnable<Boolean> cir) {
        //The attached container is the block the hopper faces; the machine sees the opposite side. The hopper's
        //facing is the blockstate property (the field the vanilla method reads is private and instance-bound).
        Direction facing = level.getBlockState(pos).getOptionalValue(HopperBlock.FACING).orElse(null);
        if (facing != null && FabricHopperTransfer.moveOneItem(level, pos.relative(facing), facing.getOpposite(), hopper, true)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void mekanism$suckFromMachine(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        //The source container is the block above the hopper's pickup position; the machine sees the DOWN side.
        //Vanilla hopper blocks and minecart hoppers both reach this entry point; anything that is not a Container
        //and non-Mekanism sources fall through to the vanilla path, including ground items.
        if (hopper instanceof Container hopperInventory
              && FabricHopperTransfer.moveOneItem(level,
              BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ()),
              Direction.DOWN, hopperInventory, false)) {
            cir.setReturnValue(true);
        }
    }
}
