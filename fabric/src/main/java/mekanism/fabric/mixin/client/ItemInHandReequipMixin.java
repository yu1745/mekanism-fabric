package mekanism.fabric.mixin.client;

import mekanism.fabric.client.GearInteractionPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandReequipMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private ItemStack mainHandItem;
    @Unique private int mekanism$lastMainSlot;
    @Unique private boolean mekanism$slotChanged;
    @Unique private boolean mekanism$allowPolicy;

    @Inject(method = "tick", at = @At("HEAD"))
    private void mekanism$trackSlot(CallbackInfo ci) {
        LocalPlayer player = minecraft.player;
        mekanism$slotChanged = false;
        mekanism$allowPolicy = player != null && !player.isHandsBusy();
        if (mekanism$allowPolicy && !mainHandItem.isEmpty() && !player.getMainHandItem().isEmpty()) {
            int selected = player.getInventory().selected;
            mekanism$slotChanged = selected != mekanism$lastMainSlot;
            mekanism$lastMainSlot = selected;
            //Vanilla later compares stack references; force that check to observe a same-object slot change too.
            if (mekanism$slotChanged && mainHandItem == player.getMainHandItem()
                  && GearInteractionPolicy.ignoresReequipComponents(mainHandItem)) {
                mainHandItem = mainHandItem.copy();
            }
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z", ordinal = 0))
    private boolean mekanism$mainHandMatches(ItemStack oldStack, ItemStack newStack) {
        if (mekanism$allowPolicy && !oldStack.isEmpty() && !newStack.isEmpty()
              && oldStack.getItem() == newStack.getItem() && GearInteractionPolicy.ignoresReequipComponents(oldStack)) {
            return !mekanism$slotChanged;
        }
        return ItemStack.matches(oldStack, newStack);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z", ordinal = 1))
    private boolean mekanism$offHandMatches(ItemStack oldStack, ItemStack newStack) {
        if (mekanism$allowPolicy && !oldStack.isEmpty() && !newStack.isEmpty()
              && oldStack.getItem() == newStack.getItem() && GearInteractionPolicy.ignoresReequipComponents(oldStack)) {
            return true;
        }
        return ItemStack.matches(oldStack, newStack);
    }
}
