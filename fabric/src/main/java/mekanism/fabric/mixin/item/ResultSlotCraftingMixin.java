package mekanism.fabric.mixin.item;

import mekanism.common.recipe.bin.BinInsertRecipe;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge fires {@code PlayerEvent.ItemCraftedEvent} from {@code ResultSlot#checkTakeAchievements} (after the output is marked crafted and before the crafting
 * matrix is drained). Fabric has no equivalent event, so invoke the Mekanism bin-insert follow-up at the same point.
 */
@Mixin(ResultSlot.class)
abstract class ResultSlotCraftingMixin {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Inject(method = "checkTakeAchievements", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/item/ItemStack;onCraftedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;I)V",
          shift = At.Shift.AFTER), require = 1)
    private void mekanism$onCrafted(ItemStack stack, CallbackInfo ci) {
        BinInsertRecipe.onCrafting(stack, craftSlots);
    }
}
