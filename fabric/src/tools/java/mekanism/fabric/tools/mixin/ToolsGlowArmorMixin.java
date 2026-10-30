package mekanism.fabric.tools.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import mekanism.tools.common.item.ItemRefinedGlowstoneArmor;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keep vanilla's actual armor pose, slot visibility and materials; glowstone only changes the supplied light. */
@Mixin(HumanoidArmorLayer.class)
abstract class ToolsGlowArmorMixin {
    @ModifyVariable(method = "renderArmorPiece", at = @At("HEAD"), argsOnly = true)
    private int mekanismtools$fullBright(int light, @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) EquipmentSlot slot) {
        return entity.getItemBySlot(slot).getItem() instanceof ItemRefinedGlowstoneArmor ? LightTexture.FULL_BRIGHT : light;
    }
}
