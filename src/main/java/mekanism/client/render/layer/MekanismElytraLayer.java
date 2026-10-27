package mekanism.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mekanism.api.MekanismAPI;
import mekanism.common.registries.MekanismItems;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric port of the official HDPE elytra layer. Vanilla 1.21.1 has no texture hook on the elytra layer (NeoForge
 * patches {@code shouldRender}/{@code getElytraTexture} in), so the render body mirrors vanilla's with the HDPE
 * texture substituted for the matching stack.
 */
public class MekanismElytraLayer<ENTITY extends LivingEntity, MODEL extends EntityModel<ENTITY>> extends ElytraLayer<ENTITY, MODEL> {

    private static final ResourceLocation HDPE_ELYTRA = ResourceLocation.fromNamespaceAndPath(MekanismAPI.MEKANISM_MODID, "textures/entity/hdpe_elytra.png");

    public MekanismElytraLayer(RenderLayerParent<ENTITY, MODEL> entityRenderer, EntityModelSet modelSet) {
        super(entityRenderer, modelSet);
    }

    private static boolean isHdpeElytra(@NotNull ItemStack stack) {
        return stack.is(MekanismItems.HDPE_REINFORCED_ELYTRA);
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int packedLight, @NotNull ENTITY livingEntity,
          float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack chest = livingEntity.getItemBySlot(EquipmentSlot.CHEST);
        if (isHdpeElytra(chest)) {
            //Vanilla render body with the HDPE texture
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.125F);
            //The vanilla elytra model field rides the access widener (private in vanilla, no texture hook to subclass).
            EntityModel<ENTITY> elytraModel = this.elytraModel;
            getParentModel().copyPropertiesTo(elytraModel);
            elytraModel.setupAnim(livingEntity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            VertexConsumer vertexConsumer = ItemRenderer.getArmorFoilBuffer(multiBufferSource, RenderType.armorCutoutNoCull(HDPE_ELYTRA), chest.hasFoil());
            elytraModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }
}
