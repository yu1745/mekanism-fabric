package mekanism.fabric.mixin.item;

import mekanism.common.item.gear.ItemHDPEElytra;
import mekanism.common.item.interfaces.IElytraFlight;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Client jump-to-glide check, matching the NeoForge patch that asks {@code canElytraFly} before sending the command. */
@Mixin(LocalPlayer.class)
abstract class ClientElytraStartMixin {

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"), require = 1)
    private boolean mekanism$alternativeElytra(ItemStack stack, Item item) {
        return stack.is(item) || (item == Items.ELYTRA && (stack.getItem() instanceof IElytraFlight || stack.getItem() instanceof ItemHDPEElytra));
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ElytraItem;isFlyEnabled(Lnet/minecraft/world/item/ItemStack;)Z"), require = 1)
    private boolean mekanism$canStartFlight(ItemStack stack) {
        if (stack.getItem() instanceof IElytraFlight flight) {
            return flight.canElytraFly(stack, (LocalPlayer) (Object) this);
        }
        return ElytraItem.isFlyEnabled(stack);
    }
}
