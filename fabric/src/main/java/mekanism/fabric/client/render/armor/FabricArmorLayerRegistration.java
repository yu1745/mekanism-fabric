package mekanism.fabric.client.render.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.concurrent.atomic.AtomicBoolean;
import mekanism.client.render.armor.FreeRunnerArmor;
import mekanism.client.render.armor.JetpackArmor;
import mekanism.client.render.armor.ScubaMaskArmor;
import mekanism.client.render.armor.ScubaTankArmor;
import mekanism.client.render.layer.MekanismArmorLayer;
import mekanism.client.render.layer.MekanismElytraLayer;
import mekanism.common.registries.MekanismItems;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric port of the official {@code EntityRenderersEvent.AddLayers} handler: the custom armor and elytra layers ride
 * every living-entity renderer that has vanilla's humanoid armor or elytra layer, exactly mirroring
 * {@code addCustomLayers} (players included through the same callback). The {@code MekaSuitArmor} gear registrations
 * stay deferred with the OBJ model-cache half; the simple gear suite registers here.
 */
public final class FabricArmorLayerRegistration {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricArmorLayers");
    private static final AtomicBoolean PROOF = new AtomicBoolean();
    private static boolean registeredStaticGear = false;

    private FabricArmorLayerRegistration() {
    }

    public static void register() {
        registerStaticGear();
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(FabricArmorLayerRegistration::addCustomLayers);
        if (PROOF.compareAndSet(false, true)) {
            LOGGER.info("MEKANISM_FABRIC_ARMOR_LAYER_PROOF callback=registered");
        }
    }

    /** The official render-properties registrations for the simple gear suite (MekaSuit stays with the OBJ half). */
    private static void registerStaticGear() {
        if (registeredStaticGear) {
            return;
        }
        registeredStaticGear = true;
        FabricGearModels.register(MekanismItems.ARMORED_JETPACK.asItem(), JetpackArmor.ARMORED_JETPACK);
        FabricGearModels.register(MekanismItems.JETPACK.asItem(), JetpackArmor.JETPACK);
        FabricGearModels.register(MekanismItems.ARMORED_FREE_RUNNERS.asItem(), FreeRunnerArmor.ARMORED_FREE_RUNNERS);
        FabricGearModels.register(MekanismItems.FREE_RUNNERS.asItem(), FreeRunnerArmor.FREE_RUNNERS);
        FabricGearModels.register(MekanismItems.SCUBA_MASK.asItem(), ScubaMaskArmor.SCUBA_MASK);
        FabricGearModels.register(MekanismItems.SCUBA_TANK.asItem(), ScubaTankArmor.SCUBA_TANK);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <LIVING extends LivingEntity, MODEL extends EntityModel<LIVING>> void addCustomLayers(
          EntityType<? extends LivingEntity> type, LivingEntityRenderer<LIVING, MODEL> renderer,
          LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper registrationHelper, EntityRendererProvider.Context context) {
        boolean addedArmor = false;
        boolean addedElytra = false;
        for (Object layerRenderer : renderer.layers) {
            if (layerRenderer == null) {
                continue;
            }
            //Only allow an exact class match, so we don't add to modded entities that only have a modded extended
            //armor or elytra layer
            Class<?> layerClass = layerRenderer.getClass();
            if (layerClass == HumanoidArmorLayer.class && !addedArmor) {
                //Note: We know that the MODEL is actually an instance of HumanoidModel, or there wouldn't be an
                // armor layer on this renderer
                registrationHelper.register((RenderLayer<LIVING, MODEL>) new MekanismArmorLayer(renderer,
                      (HumanoidArmorLayer<LIVING, HumanoidModel<LIVING>, HumanoidModel<LIVING>>) layerRenderer, context.getModelManager()));
                addedArmor = true;
            } else if (layerClass == ElytraLayer.class && !addedElytra) {
                registrationHelper.register((RenderLayer<LIVING, MODEL>) new MekanismElytraLayer(renderer, context.getModelSet()));
                addedElytra = true;
            }
        }
    }
}
