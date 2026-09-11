package mekanism.common.capabilities.heat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.api.heat.HeatAPI;
import org.junit.jupiter.api.Test;

class VariableHeatCapacitorTest {

    @Test
    void simpleFactoryUsesDefaultsAndRetainsInheritedBehavior() {
        AtomicInteger notifications = new AtomicInteger();
        VariableHeatCapacitor capacitor = VariableHeatCapacitor.create(2, () -> 275, notifications::incrementAndGet);

        assertEquals(HeatAPI.DEFAULT_INVERSE_CONDUCTION, capacitor.getInverseConduction());
        assertEquals(HeatAPI.DEFAULT_INVERSE_INSULATION, capacitor.getInverseInsulation());
        assertEquals(550, capacitor.getHeat());
        assertEquals(275, capacitor.getTemperature());

        capacitor.handleHeat(50);
        capacitor.update();
        assertEquals(600, capacitor.getHeat());
        assertEquals(1, notifications.get());
    }

    @Test
    void customSuppliersAreSampledByConstructionAndRemainLive() {
        AtomicInteger conductionReads = new AtomicInteger();
        AtomicInteger insulationReads = new AtomicInteger();
        AtomicReference<Double> conduction = new AtomicReference<>(4D);
        AtomicReference<Double> insulation = new AtomicReference<>(8D);
        VariableHeatCapacitor capacitor = VariableHeatCapacitor.create(2,
              () -> {
                  conductionReads.incrementAndGet();
                  return conduction.get();
              }, () -> {
                  insulationReads.incrementAndGet();
                  return insulation.get();
              }, null, null);

        assertEquals(1, conductionReads.get());
        assertEquals(1, insulationReads.get());
        assertEquals(4, capacitor.getInverseConduction());
        assertEquals(8, capacitor.getInverseInsulation());
        assertEquals(2, conductionReads.get());
        assertEquals(2, insulationReads.get());

        conduction.set(12D);
        insulation.set(-3D);
        assertEquals(12, capacitor.getInverseConduction());
        assertEquals(-3, capacitor.getInverseInsulation());
        assertEquals(3, conductionReads.get());
        assertEquals(3, insulationReads.get());
    }

    @Test
    void dynamicCoefficientRulesPreserveExactOfficialEdgeCases() {
        AtomicReference<Double> conduction = new AtomicReference<>(0.5D);
        AtomicReference<Double> insulation = new AtomicReference<>(0.5D);
        VariableHeatCapacitor capacitor = VariableHeatCapacitor.create(1, conduction::get, insulation::get, null, null);

        assertEquals(1, capacitor.getInverseConduction());
        assertEquals(0.5, capacitor.getInverseInsulation());

        conduction.set(Double.NaN);
        insulation.set(Double.NaN);
        assertTrue(Double.isNaN(capacitor.getInverseConduction()));
        assertTrue(Double.isNaN(capacitor.getInverseInsulation()));

        conduction.set(Double.NEGATIVE_INFINITY);
        insulation.set(Double.NEGATIVE_INFINITY);
        assertEquals(1, capacitor.getInverseConduction());
        assertEquals(Double.NEGATIVE_INFINITY, capacitor.getInverseInsulation());

        conduction.set(Double.POSITIVE_INFINITY);
        insulation.set(Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, capacitor.getInverseConduction());
        assertEquals(Double.POSITIVE_INFINITY, capacitor.getInverseInsulation());
    }

    @Test
    void factoryBypassesBasicFactoryValidation() {
        VariableHeatCapacitor capacitor = VariableHeatCapacitor.create(0.25, () -> 0.5, () -> -2, null, null);

        assertEquals(0.25, capacitor.getHeatCapacity());
        assertEquals(1, capacitor.getInverseConduction());
        assertEquals(-2, capacitor.getInverseInsulation());
        assertEquals(HeatAPI.AMBIENT_TEMP * 0.25, capacitor.getHeat());
    }

    @Test
    void coefficientSuppliersAreRequiredButAmbientAndListenerAreNullable() {
        assertThrows(NullPointerException.class, () -> VariableHeatCapacitor.create(1, null, () -> 1, null, null));
        assertThrows(NullPointerException.class, () -> VariableHeatCapacitor.create(1, () -> 1, null, null, null));

        VariableHeatCapacitor capacitor = VariableHeatCapacitor.create(1, () -> 2, () -> 3, null, null);
        assertEquals(HeatAPI.AMBIENT_TEMP, capacitor.getTemperature());
        capacitor.onContentsChanged();
    }
}
