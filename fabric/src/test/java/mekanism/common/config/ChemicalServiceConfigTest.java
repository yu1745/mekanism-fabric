package mekanism.common.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalDataContext;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.MekanismDataMapType;
import mekanism.api.datamaps.chemical.attribute.ChemicalRadioactivity;
import mekanism.api.text.ITooltipHelper;
import mekanism.common.registries.MekanismChemicals;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.fabric.config.MekanismConfigSpec;
import mekanism.test.ChemicalTestBootstrap;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ChemicalServiceConfigTest {
    @BeforeAll static void bootstrap() { ChemicalTestBootstrap.initialize(); }
    @AfterEach void clear() { ChemicalDataContext.clear(); ChemicalConfigAccess.resetServerPolicy(); }

    @Test
    void completeDataMapServiceOwnsAllDescriptors() {
        IMekanismDataMapTypes service = IMekanismDataMapTypes.INSTANCE;
        assertFalse(service.mekaSuitAbsorption().synchronizedToClient());
        assertAll(() -> assertTrue(service.chemicalSolidTag().synchronizedToClient()),
              () -> assertTrue(service.chemicalFuel().synchronizedToClient()),
              () -> assertTrue(service.chemicalRadioactivity().synchronizedToClient()),
              () -> assertTrue(service.cooledChemicalCoolant().synchronizedToClient()),
              () -> assertTrue(service.heatedChemicalCoolant().synchronizedToClient()));
    }

    @Test
    void tooltipServiceImplementsAllFormattingMethods() {
        ITooltipHelper helper = ITooltipHelper.INSTANCE;
        assertAll(() -> assertNotNull(helper.getEnergyPerMBDisplayShort(10)),
              () -> assertNotNull(helper.getRadioactivityDisplayShort(0.25)),
              () -> assertNotNull(helper.getTemperatureDisplayShort(300)),
              () -> assertFalse(helper.getFormattedNumber(12_345).isBlank()),
              () -> assertNotNull(helper.getPercent(0.5)));
    }

    @Test
    void commonConfigStrictlyEncodesFiveValues(@TempDir Path directory) throws Exception {
        CommonConfig config = new CommonConfig();
        Path file = directory.resolve("common.toml");
        config.getConfigSpec().load(file);
        assertEquals(5, config.getConfigSpec().size());
        assertEquals(UnitDisplayUtils.EnergyUnit.FORGE_ENERGY, config.energyUnit.get());
        assertTrue(Files.readString(file).contains("energyType = \"FORGE_ENERGY\""));
        assertFalse(config.getConfigSpec().isValidValue("energyType", "forge_energy"));
        config.getConfigSpec().unload();
    }

    @Test
    void radiationPolicyChangesRealTankAdmission() {
        ResourceKey<Chemical> key = MekanismChemicals.NUCLEAR_WASTE.getKey();
        Map<MekanismDataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> maps = new IdentityHashMap<>();
        maps.put(IMekanismDataMapTypes.INSTANCE.chemicalRadioactivity(), Map.of(key, new ChemicalRadioactivity(1.0E-5)));
        BasicChemicalTank tank = (BasicChemicalTank) BasicChemicalTank.create(100, null);
        ChemicalStack waste = new ChemicalStack(MekanismChemicals.NUCLEAR_WASTE, 1);
        ChemicalDataContext.bind(new ChemicalDataContext(1, maps, true, 2.5, false));
        assertSame(waste, tank.insert(waste, Action.EXECUTE, AutomationType.EXTERNAL));
        ChemicalDataContext.bind(new ChemicalDataContext(2, maps, false, 2.5, false));
        assertTrue(tank.insert(waste, Action.EXECUTE, AutomationType.EXTERNAL).isEmpty());
    }

    @Test
    void serverAndClientPoliciesStayContextLocal() {
        ChemicalDataContext server = new ChemicalDataContext(4, Map.of(), true, 4.0, false);
        ChemicalDataContext client = new ChemicalDataContext(7, Map.of(), false, 3.0, true);
        ChemicalDataContext.bind(server);
        assertAll(() -> assertTrue(ChemicalConfigAccess.radiationEnabled()), () -> assertEquals(4.0, ChemicalConfigAccess.feConversionRate()));
        ChemicalDataContext.bind(client);
        assertAll(() -> assertFalse(ChemicalConfigAccess.radiationEnabled()), () -> assertFalse(ChemicalConfigAccess.forgeEnabled()));
        ChemicalDataContext.bind(server);
        assertEquals(4, ChemicalDataContext.current().epoch());
    }
}
