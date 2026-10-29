package mekanism.fabric.client.render.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import mekanism.client.render.armor.ICustomArmor;
import mekanism.common.registries.MekanismItems;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Registers the same jetpack gear renderers as the official optional accessory integration. */
public final class FabricAccessoryRenderRegistration {
    private FabricAccessoryRenderRegistration() {
    }

    public static void register() {
        AccessoriesRendererRegistry.registerRenderer(MekanismItems.JETPACK.asItem(), GearRenderer::new);
        AccessoriesRendererRegistry.registerRenderer(MekanismItems.ARMORED_JETPACK.asItem(), GearRenderer::new);
    }

    private static final class GearRenderer implements AccessoryRenderer {
        @Override
        public <T extends LivingEntity> void render(ItemStack stack, SlotReference reference, PoseStack matrices, EntityModel<T> model,
              MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
              float netHeadYaw, float headPitch) {
            ICustomArmor gear = FabricGearModels.gearModel(stack);
            if (gear != null && model instanceof HumanoidModel<?> humanoid) {
                gear.render(humanoid, matrices, buffers, light, OverlayTexture.NO_OVERLAY, partialTicks, stack.hasFoil(), reference.entity(), stack);
            }
        }
    }
}
