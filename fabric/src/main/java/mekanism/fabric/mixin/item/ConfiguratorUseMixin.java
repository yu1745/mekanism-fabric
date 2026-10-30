package mekanism.fabric.mixin.item;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mekanism.common.block.BlockBounding;
import mekanism.common.block.BlockCardboardBox;
import mekanism.common.block.BlockMekanism;
import mekanism.common.registries.MekanismItems;
import mekanism.common.tags.MekanismTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import mekanism.common.item.ItemConfigurator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Original configurator bypass plus the bounded CommonPlayerTracker block/item-use overrides. */
@Mixin(ServerPlayerGameMode.class)
abstract class ConfiguratorUseMixin {
    @ModifyExpressionValue(method = "useItemOn", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/server/level/ServerPlayer;isSecondaryUseActive()Z"))
    private boolean mekanism$configuratorBlockUse(boolean secondary, ServerPlayer player, Level level, ItemStack stack,
          InteractionHand hand, BlockHitResult hit) {
        var block = level.getBlockState(hit.getBlockPos()).getBlock();
        if (isExternalWrench(stack)) {
            if (block instanceof BlockMekanism || block instanceof BlockBounding) return false;
        } else if (player.isShiftKeyDown() && block instanceof BlockCardboardBox) {
            return false;
        }
        return secondary && !ItemConfigurator.doesSneakBypassUse(player, hit.getBlockPos());
    }

    private static boolean isExternalWrench(ItemStack stack) {
        return stack.is(MekanismTags.Items.CONFIGURATORS) && !stack.is(MekanismItems.CONFIGURATOR);
    }

    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult mekanism$preventItemUseAfterUnbox(ItemStack stack, UseOnContext context,
          Operation<InteractionResult> original) {
        var player = context.getPlayer();
        if (!isExternalWrench(stack) && player != null && player.isShiftKeyDown()
              && context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof BlockCardboardBox) {
            return InteractionResult.PASS;
        }
        return original.call(stack, context);
    }
}
