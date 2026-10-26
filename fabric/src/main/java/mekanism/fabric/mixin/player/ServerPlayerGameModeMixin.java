package mekanism.fabric.mixin.player;

import mekanism.api.security.IBlockSecurityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric port delta: the official NeoForge {@code BlockEvent.BreakEvent} listener vetoed breaks of blocks the player has no
 * security access to. Fabric has no cancellable break event, so this rejects the authoritative server-side break before any
 * block mutation happens (the same {@link IBlockSecurityUtils#canAccess} decision the listener made).
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {

    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void mekanism$rejectInaccessibleBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !IBlockSecurityUtils.INSTANCE.canAccess(player, level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
