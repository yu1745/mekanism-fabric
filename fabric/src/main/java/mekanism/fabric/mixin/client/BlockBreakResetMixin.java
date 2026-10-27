package mekanism.fabric.mixin.client;

import mekanism.fabric.client.GearInteractionPolicy;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultiPlayerGameMode.class)
abstract class BlockBreakResetMixin {

    @Redirect(method = "sameDestroyTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean mekanism$preserveBreakingOnComponentChange(ItemStack current, ItemStack previous) {
        if (GearInteractionPolicy.ignoresBreakResetComponents(previous)) {
            return previous.getItem() == current.getItem();
        }
        return ItemStack.isSameItemSameComponents(current, previous);
    }
}
