package mekanism.fabric.mixin.entity;

import mekanism.common.item.interfaces.IElytraFlight;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla keeps fall flying alive only for {@code Items.ELYTRA}; NeoForge replaced the condition with
 * {@code ItemStack#canElytraFly}/{@code #elytraFlightTick}. These redirects answer the same two decision points for items
 * implementing {@link IElytraFlight}, including the per-tick energy check.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityElytraMixin {

    @Shadow
    protected int fallFlyTicks;

    @Redirect(method = "updateFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"), require = 1)
    private boolean mekanism$alternativeElytra(ItemStack stack, Item item) {
        return stack.is(item) || (item == Items.ELYTRA && stack.getItem() instanceof IElytraFlight);
    }

    @Redirect(method = "updateFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ElytraItem;isFlyEnabled(Lnet/minecraft/world/item/ItemStack;)Z"), require = 1)
    private boolean mekanism$flightTick(ItemStack stack) {
        if (stack.getItem() instanceof IElytraFlight flight) {
            return flight.elytraFlightTick(stack, (LivingEntity) (Object) this, fallFlyTicks);
        }
        return ElytraItem.isFlyEnabled(stack);
    }
}
