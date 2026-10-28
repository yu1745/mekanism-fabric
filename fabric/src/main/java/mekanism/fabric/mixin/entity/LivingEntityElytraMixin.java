package mekanism.fabric.mixin.entity;

import mekanism.common.item.gear.ItemHDPEElytra;
import mekanism.common.item.interfaces.IElytraFlight;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores the NeoForge elytra hooks for Mekanism equipment without changing vanilla elytra behavior. */
@Mixin(LivingEntity.class)
abstract class LivingEntityElytraMixin extends Entity {

    protected LivingEntityElytraMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Shadow protected int fallFlyTicks;

    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void mekanism$updateCustomFlight(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        ItemStack stack = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (stack.getItem() instanceof IElytraFlight flight) {
            boolean flying = entity.isFallFlying() && !entity.onGround() && !entity.isPassenger() && !entity.hasEffect(MobEffects.LEVITATION)
                  && flight.canElytraFly(stack, entity) && flight.elytraFlightTick(stack, entity, fallFlyTicks);
            if (!entity.level().isClientSide) {
                setSharedFlag(7, flying);
            }
            // The custom item owns its own energy, damage and ELYTRA_GLIDE events, as on NeoForge.
            ci.cancel();
        }
    }

    @Redirect(method = "updateFallFlying", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"), require = 1)
    private boolean mekanism$hdpeElytra(ItemStack stack, Item item) {
        return stack.is(item) || (item == Items.ELYTRA && stack.getItem() instanceof ItemHDPEElytra);
    }
}
