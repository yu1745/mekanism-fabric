package mekanism.fabric.client.render;

import java.util.function.Supplier;
import mekanism.client.model.ModelArmoredFreeRunners;
import mekanism.client.model.ModelArmoredJetpack;
import mekanism.client.model.ModelAtomicDisassembler;
import mekanism.client.model.ModelEnergyCore;
import mekanism.client.model.ModelFlamethrower;
import mekanism.client.model.ModelFreeRunners;
import mekanism.client.model.ModelIndustrialAlarm;
import mekanism.client.model.ModelJetpack;
import mekanism.client.model.ModelScubaMask;
import mekanism.client.model.ModelScubaTank;
import mekanism.client.model.ModelIndustrialAlarm;
import mekanism.client.render.tileentity.RenderBin;
import mekanism.client.render.tileentity.RenderDigitalMiner;
import mekanism.client.render.tileentity.RenderDimensionalStabilizer;
import mekanism.client.render.tileentity.RenderDynamicTank;
import mekanism.client.render.tileentity.RenderEnergyCube;
import mekanism.client.render.tileentity.RenderFluidTank;
import mekanism.client.render.tileentity.RenderIndustrialAlarm;
import mekanism.client.render.tileentity.RenderNutritionalLiquifier;
import mekanism.client.render.tileentity.RenderPersonalChest;
import mekanism.client.render.tileentity.RenderPigmentMixer;
import mekanism.client.render.tileentity.RenderSPS;
import mekanism.client.render.tileentity.RenderSeismicVibrator;
import mekanism.client.render.tileentity.RenderTeleporter;
import mekanism.client.render.tileentity.RenderThermalEvaporationPlant;
import mekanism.client.render.tileentity.RenderThermoelectricBoiler;
import mekanism.common.registries.MekanismTileEntityTypes;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/**
 * Tile-lane slice of the official {@code ClientRegistration.registerRenderers}: the fifteen tile entity renderers
 * through vanilla {@code BlockEntityRenderers.register} plus the two BER model layers through Fabric's
 * {@link EntityModelLayerRegistry} (the official {@code EntityRenderersEvent.RegisterLayerDefinitions} handler).
 *
 * <p>The transmitter renderers of the official block stay deferred: on this port the transmitter shell visual is
 * owned by the baked-model seam ({@code FabricTransmitterModels}), and the contents overlay re-bakes through the
 * official OBJ geometry loader, which has no Fabric counterpart yet - see {@code docs/TASK052_RESUME_2026-10-26.md}.</p>
 */
public final class FabricTileEntityRendererRegistration {

    private FabricTileEntityRendererRegistration() {
    }

    public static void register() {
        //The two Java models the tile renderers construct per instance, mirroring the official layer registration.
        registerLayer(ModelEnergyCore.CORE_LAYER, ModelEnergyCore::createLayerDefinition);
        registerLayer(ModelIndustrialAlarm.ALARM_LAYER, ModelIndustrialAlarm::createLayerDefinition);
        //The gear item ISBER layers (the official registerLayerDefinitions list minus the transporter-box layer).
        registerLayer(ModelJetpack.JETPACK_LAYER, ModelJetpack::createLayerDefinition);
        registerLayer(ModelArmoredJetpack.ARMORED_JETPACK_LAYER, ModelArmoredJetpack::createLayerDefinition);
        registerLayer(ModelAtomicDisassembler.DISASSEMBLER_LAYER, ModelAtomicDisassembler::createLayerDefinition);
        registerLayer(ModelFlamethrower.FLAMETHROWER_LAYER, ModelFlamethrower::createLayerDefinition);
        registerLayer(ModelFreeRunners.FREE_RUNNER_LAYER, ModelFreeRunners::createLayerDefinition);
        registerLayer(ModelArmoredFreeRunners.ARMORED_FREE_RUNNER_LAYER, ModelArmoredFreeRunners::createLayerDefinition);
        registerLayer(ModelScubaMask.MASK_LAYER, ModelScubaMask::createLayerDefinition);
        registerLayer(ModelScubaTank.TANK_LAYER, ModelScubaTank::createLayerDefinition);
        //The transporter box rides the transporter BER lane (the official registerLayerDefinition at ClientRegistration:373).
        registerLayer(mekanism.client.model.ModelTransporterBox.BOX_LAYER, mekanism.client.model.ModelTransporterBox::createLayerDefinition);

        int renderers = 0;
        renderers += bind(RenderThermoelectricBoiler::new, MekanismTileEntityTypes.BOILER_CASING, MekanismTileEntityTypes.BOILER_VALVE);
        renderers += bind(RenderDynamicTank::new, MekanismTileEntityTypes.DYNAMIC_TANK, MekanismTileEntityTypes.DYNAMIC_VALVE);
        renderers += bind(RenderDigitalMiner::new, MekanismTileEntityTypes.DIGITAL_MINER);
        renderers += bind(RenderDimensionalStabilizer::new, MekanismTileEntityTypes.DIMENSIONAL_STABILIZER);
        renderers += bind(RenderPersonalChest::new, MekanismTileEntityTypes.PERSONAL_CHEST);
        renderers += bind(RenderNutritionalLiquifier::new, MekanismTileEntityTypes.NUTRITIONAL_LIQUIFIER);
        renderers += bind(RenderPigmentMixer::new, MekanismTileEntityTypes.PIGMENT_MIXER);
        renderers += bind(RenderSeismicVibrator::new, MekanismTileEntityTypes.SEISMIC_VIBRATOR);
        renderers += bind(RenderTeleporter::new, MekanismTileEntityTypes.TELEPORTER);
        renderers += bind(RenderThermalEvaporationPlant::new, MekanismTileEntityTypes.THERMAL_EVAPORATION_CONTROLLER);
        renderers += bind(RenderIndustrialAlarm::new, MekanismTileEntityTypes.INDUSTRIAL_ALARM);
        renderers += bind(RenderSPS::new, MekanismTileEntityTypes.SPS_CASING, MekanismTileEntityTypes.SPS_PORT);
        renderers += bind(RenderBin::new, MekanismTileEntityTypes.BASIC_BIN, MekanismTileEntityTypes.ADVANCED_BIN, MekanismTileEntityTypes.ELITE_BIN,
              MekanismTileEntityTypes.ULTIMATE_BIN, MekanismTileEntityTypes.CREATIVE_BIN);
        renderers += bind(RenderEnergyCube::new, MekanismTileEntityTypes.BASIC_ENERGY_CUBE, MekanismTileEntityTypes.ADVANCED_ENERGY_CUBE,
              MekanismTileEntityTypes.ELITE_ENERGY_CUBE, MekanismTileEntityTypes.ULTIMATE_ENERGY_CUBE, MekanismTileEntityTypes.CREATIVE_ENERGY_CUBE);
        renderers += bind(RenderFluidTank::new, MekanismTileEntityTypes.BASIC_FLUID_TANK, MekanismTileEntityTypes.ADVANCED_FLUID_TANK,
              MekanismTileEntityTypes.ELITE_FLUID_TANK, MekanismTileEntityTypes.ULTIMATE_FLUID_TANK, MekanismTileEntityTypes.CREATIVE_FLUID_TANK);
        //The port-only drive-array replacement BER (the official wrapper read the drive states from model data).
        renderers += bind(mekanism.client.render.tileentity.RenderQIODriveArray::new, MekanismTileEntityTypes.QIO_DRIVE_ARRAY);
        //The transmitter contents half: the four static-content renderers (the shell visual stays on the baked-model
        //seam; the transporter animation lane stays deferred).
        renderers += bind(mekanism.client.render.transmitter.RenderMechanicalPipe::new, MekanismTileEntityTypes.BASIC_MECHANICAL_PIPE,
              MekanismTileEntityTypes.ADVANCED_MECHANICAL_PIPE, MekanismTileEntityTypes.ELITE_MECHANICAL_PIPE,
              MekanismTileEntityTypes.ULTIMATE_MECHANICAL_PIPE);
        renderers += bind(mekanism.client.render.transmitter.RenderPressurizedTube::new, MekanismTileEntityTypes.BASIC_PRESSURIZED_TUBE,
              MekanismTileEntityTypes.ADVANCED_PRESSURIZED_TUBE, MekanismTileEntityTypes.ELITE_PRESSURIZED_TUBE,
              MekanismTileEntityTypes.ULTIMATE_PRESSURIZED_TUBE);
        renderers += bind(mekanism.client.render.transmitter.RenderThermodynamicConductor::new, MekanismTileEntityTypes.BASIC_THERMODYNAMIC_CONDUCTOR,
              MekanismTileEntityTypes.ADVANCED_THERMODYNAMIC_CONDUCTOR, MekanismTileEntityTypes.ELITE_THERMODYNAMIC_CONDUCTOR,
              MekanismTileEntityTypes.ULTIMATE_THERMODYNAMIC_CONDUCTOR);
        renderers += bind(mekanism.client.render.transmitter.RenderUniversalCable::new, MekanismTileEntityTypes.BASIC_UNIVERSAL_CABLE,
              MekanismTileEntityTypes.ADVANCED_UNIVERSAL_CABLE, MekanismTileEntityTypes.ELITE_UNIVERSAL_CABLE,
              MekanismTileEntityTypes.ULTIMATE_UNIVERSAL_CABLE);
        //The transporter animation lane: in-transit stacks, the color box, and the diversion overlay.
        renderers += bind(mekanism.client.render.transmitter.RenderLogisticalTransporter::new, MekanismTileEntityTypes.DIVERSION_TRANSPORTER,
              MekanismTileEntityTypes.RESTRICTIVE_TRANSPORTER, MekanismTileEntityTypes.BASIC_LOGISTICAL_TRANSPORTER,
              MekanismTileEntityTypes.ADVANCED_LOGISTICAL_TRANSPORTER, MekanismTileEntityTypes.ELITE_LOGISTICAL_TRANSPORTER,
              MekanismTileEntityTypes.ULTIMATE_LOGISTICAL_TRANSPORTER);
        org.slf4j.LoggerFactory.getLogger("Mekanism/FabricTileEntityRenderers")
              .info("MEKANISM_FABRIC_BER_PROOF tile_types={} renderers=21 layers=11", renderers);
    }

    @SafeVarargs
    private static <T extends BlockEntity> int bind(BlockEntityRendererProvider<? super T> provider, Supplier<? extends BlockEntityType<? extends T>>... types) {
        for (Supplier<? extends BlockEntityType<? extends T>> type : types) {
            BlockEntityRenderers.register(type.get(), provider);
        }
        return types.length;
    }

    private static void registerLayer(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
        EntityModelLayerRegistry.registerModelLayer(location, definition::get);
    }
}
