package mekanism.fabric.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The port's item decoration surface: vanilla draws the count and durability decorations over GUI items, and this
 * hook appends the registered item decorators (the chemical/fluid bars and the transmitter type icons).
 */
@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
          at = @At("TAIL"))
    private void mekanism$renderDecorators(net.minecraft.client.gui.Font font, ItemStack stack, int x, int y, String string, CallbackInfo ci) {
        mekanism.client.render.item.ItemDecorators.render((GuiGraphics) (Object) this, font, stack, x, y);
    }
}
