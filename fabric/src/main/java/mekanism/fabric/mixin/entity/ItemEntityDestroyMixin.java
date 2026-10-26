package mekanism.fabric.mixin.entity;

import mekanism.common.item.interfaces.IDropContentsOnDestroy;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla calls {@code ItemStack#onDestroyed(ItemEntity)} without the destroying source; NeoForge passed it so contained
 * contents could be security-checked against the destroying player. Forward the real source to the Mekanism interface.
 */
@Mixin(ItemEntity.class)
abstract class ItemEntityDestroyMixin {

    @Redirect(method = "hurt", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;)V"), require = 1)
    private void mekanism$onDestroyed(ItemStack stack, ItemEntity item, DamageSource source, float amount) {
        if (stack.getItem() instanceof IDropContentsOnDestroy droppable) {
            droppable.onDestroyed(item, source);
        } else {
            stack.onDestroyed(item);
        }
    }
}
