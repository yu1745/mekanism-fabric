package mekanism.fabric.client;

import mekanism.client.gui.machine.GuiElectricMachine;
import mekanism.client.gui.machine.GuiElectrolyticSeparator;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registries.MekanismContainerTypes;
import mekanism.common.tile.machine.TileEntityElectrolyticSeparator;
import mekanism.common.tile.machine.TileEntityEnrichmentChamber;
import mekanism.common.tile.machine.TileEntityEnergizedSmelter;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric port delta: upstream registers these from ClientRegistration via NeoForge's RegisterMenuScreensEvent and
 * ClientRegistrationUtil.registerScreen; on Fabric the vanilla {@link MenuScreens#register} is called directly during
 * client init. The remaining machine screens land with the client-registration batch.
 */
public final class FabricScreenRegistration {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricScreenRegistration");

    private FabricScreenRegistration() {
    }

    public static void register() {
        MenuScreens.register(MekanismContainerTypes.ENRICHMENT_CHAMBER.get(),
              GuiElectricMachine<TileEntityEnrichmentChamber, MekanismTileContainer<TileEntityEnrichmentChamber>>::new);
        MenuScreens.register(MekanismContainerTypes.ENERGIZED_SMELTER.get(),
              GuiElectricMachine<TileEntityEnergizedSmelter, MekanismTileContainer<TileEntityEnergizedSmelter>>::new);
        MenuScreens.register(MekanismContainerTypes.ELECTROLYTIC_SEPARATOR.get(), GuiElectrolyticSeparator::new);
        //The client-registration screen batch: every admitted GUI binds its menu type here (the official
        //ClientRegistration registerScreen fan-out).
        MenuScreens.register(MekanismContainerTypes.MODULE_TWEAKER.get(), mekanism.client.gui.GuiModuleTweaker::new);
        MenuScreens.register(MekanismContainerTypes.DICTIONARY.get(), mekanism.client.gui.item.GuiDictionary::new);
        MenuScreens.register(MekanismContainerTypes.QIO_FREQUENCY_SELECT_ITEM.get(), mekanism.client.gui.qio.GuiQIOItemFrequencySelect::new);
        MenuScreens.register(MekanismContainerTypes.PORTABLE_QIO_DASHBOARD.get(), mekanism.client.gui.qio.GuiPortableQIODashboard::new);
        MenuScreens.register(MekanismContainerTypes.CHEMICAL_CRYSTALLIZER.get(), mekanism.client.gui.machine.GuiChemicalCrystallizer::new);
        MenuScreens.register(MekanismContainerTypes.CHEMICAL_DISSOLUTION_CHAMBER.get(), mekanism.client.gui.machine.GuiChemicalDissolutionChamber::new);
        MenuScreens.register(MekanismContainerTypes.CHEMICAL_INFUSER.get(), mekanism.client.gui.machine.GuiChemicalInfuser::new);
        MenuScreens.register(MekanismContainerTypes.CHEMICAL_OXIDIZER.get(), mekanism.client.gui.machine.GuiChemicalOxidizer::new);
        MenuScreens.register(MekanismContainerTypes.CHEMICAL_WASHER.get(), mekanism.client.gui.machine.GuiChemicalWasher::new);
        MenuScreens.register(MekanismContainerTypes.COMBINER.get(), mekanism.client.gui.machine.GuiCombiner::new);
        MenuScreens.register(MekanismContainerTypes.DIGITAL_MINER.get(), mekanism.client.gui.machine.GuiDigitalMiner::new);
        MenuScreens.register(MekanismContainerTypes.DYNAMIC_TANK.get(), mekanism.client.gui.GuiDynamicTank::new);
        MenuScreens.register(MekanismContainerTypes.ELECTRIC_PUMP.get(), mekanism.client.gui.machine.GuiElectricPump::new);
        MenuScreens.register(MekanismContainerTypes.FLUIDIC_PLENISHER.get(), mekanism.client.gui.machine.GuiFluidicPlenisher::new);
        MenuScreens.register(MekanismContainerTypes.FORMULAIC_ASSEMBLICATOR.get(), mekanism.client.gui.machine.GuiFormulaicAssemblicator::new);
        MenuScreens.register(MekanismContainerTypes.FUELWOOD_HEATER.get(), mekanism.client.gui.machine.GuiFuelwoodHeater::new);
        MenuScreens.register(MekanismContainerTypes.LASER_AMPLIFIER.get(), mekanism.client.gui.GuiLaserAmplifier::new);
        MenuScreens.register(MekanismContainerTypes.LASER_TRACTOR_BEAM.get(), mekanism.client.gui.GuiLaserTractorBeam::new);
        MenuScreens.register(MekanismContainerTypes.METALLURGIC_INFUSER.get(), mekanism.client.gui.machine.GuiMetallurgicInfuser::new);
        MenuScreens.register(MekanismContainerTypes.OREDICTIONIFICATOR.get(), mekanism.client.gui.machine.GuiOredictionificator::new);
        MenuScreens.register(MekanismContainerTypes.PRECISION_SAWMILL.get(), mekanism.client.gui.machine.GuiPrecisionSawmill::new);
        MenuScreens.register(MekanismContainerTypes.PRESSURIZED_REACTION_CHAMBER.get(), mekanism.client.gui.machine.GuiPRC::new);
        MenuScreens.register(MekanismContainerTypes.QUANTUM_ENTANGLOPORTER.get(), mekanism.client.gui.GuiQuantumEntangloporter::new);
        MenuScreens.register(MekanismContainerTypes.RESISTIVE_HEATER.get(), mekanism.client.gui.machine.GuiResistiveHeater::new);
        MenuScreens.register(MekanismContainerTypes.ROTARY_CONDENSENTRATOR.get(), mekanism.client.gui.machine.GuiRotaryCondensentrator::new);
        MenuScreens.register(MekanismContainerTypes.SECURITY_DESK.get(), mekanism.client.gui.GuiSecurityDesk::new);
        MenuScreens.register(MekanismContainerTypes.MODIFICATION_STATION.get(), mekanism.client.gui.GuiModificationStation::new);
        MenuScreens.register(MekanismContainerTypes.ISOTOPIC_CENTRIFUGE.get(), mekanism.client.gui.machine.GuiIsotopicCentrifuge::new);
        MenuScreens.register(MekanismContainerTypes.NUTRITIONAL_LIQUIFIER.get(), mekanism.client.gui.machine.GuiNutritionalLiquifier::new);
        MenuScreens.register(MekanismContainerTypes.ANTIPROTONIC_NUCLEOSYNTHESIZER.get(), mekanism.client.gui.machine.GuiAntiprotonicNucleosynthesizer::new);
        MenuScreens.register(MekanismContainerTypes.PIGMENT_EXTRACTOR.get(), mekanism.client.gui.machine.GuiPigmentExtractor::new);
        MenuScreens.register(MekanismContainerTypes.PIGMENT_MIXER.get(), mekanism.client.gui.machine.GuiPigmentMixer::new);
        MenuScreens.register(MekanismContainerTypes.PAINTING_MACHINE.get(), mekanism.client.gui.machine.GuiPaintingMachine::new);
        MenuScreens.register(MekanismContainerTypes.SEISMIC_VIBRATOR.get(), mekanism.client.gui.machine.GuiSeismicVibrator::new);
        MenuScreens.register(MekanismContainerTypes.SOLAR_NEUTRON_ACTIVATOR.get(), mekanism.client.gui.machine.GuiSolarNeutronActivator::new);
        MenuScreens.register(MekanismContainerTypes.TELEPORTER.get(), mekanism.client.gui.GuiTeleporter::new);
        MenuScreens.register(MekanismContainerTypes.THERMAL_EVAPORATION_CONTROLLER.get(), mekanism.client.gui.GuiThermalEvaporationController::new);
        MenuScreens.register(MekanismContainerTypes.QIO_DRIVE_ARRAY.get(), mekanism.client.gui.qio.GuiQIODriveArray::new);
        MenuScreens.register(MekanismContainerTypes.QIO_DASHBOARD.get(), mekanism.client.gui.qio.GuiQIODashboard::new);
        MenuScreens.register(MekanismContainerTypes.QIO_IMPORTER.get(), mekanism.client.gui.qio.GuiQIOImporter::new);
        MenuScreens.register(MekanismContainerTypes.QIO_EXPORTER.get(), mekanism.client.gui.qio.GuiQIOExporter::new);
        MenuScreens.register(MekanismContainerTypes.QIO_REDSTONE_ADAPTER.get(), mekanism.client.gui.qio.GuiQIORedstoneAdapter::new);
        MenuScreens.register(MekanismContainerTypes.SPS.get(), mekanism.client.gui.GuiSPS::new);
        MenuScreens.register(MekanismContainerTypes.DIMENSIONAL_STABILIZER.get(), mekanism.client.gui.GuiDimensionalStabilizer::new);
        MenuScreens.register(MekanismContainerTypes.FACTORY.get(), mekanism.client.gui.machine.GuiFactory::new);
        MenuScreens.register(MekanismContainerTypes.CHEMICAL_TANK.get(), mekanism.client.gui.GuiChemicalTank::new);
        MenuScreens.register(MekanismContainerTypes.FLUID_TANK.get(), mekanism.client.gui.GuiFluidTank::new);
        MenuScreens.register(MekanismContainerTypes.ENERGY_CUBE.get(), mekanism.client.gui.GuiEnergyCube::new);
        MenuScreens.register(MekanismContainerTypes.INDUCTION_MATRIX.get(), mekanism.client.gui.GuiInductionMatrix::new);
        MenuScreens.register(MekanismContainerTypes.THERMOELECTRIC_BOILER.get(), mekanism.client.gui.GuiThermoelectricBoiler::new);
        MenuScreens.register(MekanismContainerTypes.PERSONAL_STORAGE_ITEM.get(), mekanism.client.gui.item.GuiPersonalStorageItem::new);
        MenuScreens.register(MekanismContainerTypes.PERSONAL_STORAGE_BLOCK.get(), mekanism.client.gui.GuiPersonalStorageTile::new);
        MenuScreens.register(MekanismContainerTypes.DIGITAL_MINER_CONFIG.get(), mekanism.client.gui.machine.GuiDigitalMinerConfig::new);
        MenuScreens.register(MekanismContainerTypes.LOGISTICAL_SORTER.get(), mekanism.client.gui.GuiLogisticalSorter::new);
        MenuScreens.register(MekanismContainerTypes.QIO_FREQUENCY_SELECT_TILE.get(), mekanism.client.gui.qio.GuiQIOTileFrequencySelect::new);
        MenuScreens.register(MekanismContainerTypes.BOILER_STATS.get(), mekanism.client.gui.GuiBoilerStats::new);
        MenuScreens.register(MekanismContainerTypes.MATRIX_STATS.get(), mekanism.client.gui.GuiMatrixStats::new);
        //The robit GUI family: the main panel plus the four switchable panels (the skin select opens from the main).
        MenuScreens.register(MekanismContainerTypes.MAIN_ROBIT.get(), mekanism.client.gui.robit.GuiRobitMain::new);
        MenuScreens.register(MekanismContainerTypes.INVENTORY_ROBIT.get(), mekanism.client.gui.robit.GuiRobitInventory::new);
        MenuScreens.register(MekanismContainerTypes.SMELTING_ROBIT.get(), mekanism.client.gui.robit.GuiRobitSmelting::new);
        MenuScreens.register(MekanismContainerTypes.CRAFTING_ROBIT.get(), mekanism.client.gui.robit.GuiRobitCrafting::new);
        MenuScreens.register(MekanismContainerTypes.REPAIR_ROBIT.get(), mekanism.client.gui.robit.GuiRobitRepair::new);
        List<String> menuIds = new ArrayList<>();
        menuIds.add(String.valueOf(BuiltInRegistries.MENU.getKey(MekanismContainerTypes.ENRICHMENT_CHAMBER.get())));
        menuIds.add(String.valueOf(BuiltInRegistries.MENU.getKey(MekanismContainerTypes.ENERGIZED_SMELTER.get())));
        menuIds.add(String.valueOf(BuiltInRegistries.MENU.getKey(MekanismContainerTypes.ELECTROLYTIC_SEPARATOR.get())));
        menuIds.add(String.valueOf(BuiltInRegistries.MENU.getKey(MekanismContainerTypes.MAIN_ROBIT.get())));
        LOGGER.info("MEKANISM_FABRIC_SCREEN_REGISTRATION_PROOF menus={}", String.join(",", menuIds));
    }
}
