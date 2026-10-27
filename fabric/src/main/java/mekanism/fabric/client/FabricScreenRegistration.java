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
