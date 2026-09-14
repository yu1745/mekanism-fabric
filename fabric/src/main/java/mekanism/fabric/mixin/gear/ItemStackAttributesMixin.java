package mekanism.fabric.mixin.gear;

import java.util.function.BiConsumer;
import mekanism.api.gear.GearAttributeContext;
import mekanism.common.item.interfaces.IHasConditionalAttributes;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Per-stack conditional gear attributes, including tooltip queries; vanilla enchantment modifiers retain their original tail order. */
@Mixin(ItemStack.class)
public abstract class ItemStackAttributesMixin {
    @Unique
    private ItemAttributeModifiers mekanism$attributes(ItemStack stack, IHasConditionalAttributes item) {
        ItemAttributeModifiers original = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (original.modifiers().isEmpty()) original = stack.getItem().getDefaultAttributeModifiers();
        GearAttributeContext context = new GearAttributeContext(stack, original);
        item.adjustAttributes(context);
        return context.build();
    }

    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true)
    private void mekanism$groupModifiers(EquipmentSlotGroup slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.getItem() instanceof IHasConditionalAttributes item) {
            mekanism$attributes(stack, item).forEach(slot, consumer);
            EnchantmentHelper.forEachModifier(stack, slot, consumer);
            ci.cancel();
        }
    }

    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true)
    private void mekanism$slotModifiers(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer, CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.getItem() instanceof IHasConditionalAttributes item) {
            mekanism$attributes(stack, item).forEach(slot, consumer);
            EnchantmentHelper.forEachModifier(stack, slot, consumer);
            ci.cancel();
        }
    }
}
