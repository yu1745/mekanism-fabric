package mekanism.common.tier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import mekanism.api.tier.BaseTier;
import mekanism.common.config.BaseMekanismConfig;
import mekanism.common.config.MekanismConfigType;
import mekanism.common.config.value.CachedDoubleValue;
import mekanism.common.config.value.CachedIntValue;
import mekanism.common.config.value.CachedLongValue;
import mekanism.fabric.config.MekanismConfigSpec;
import org.junit.jupiter.api.Test;

class TierMetadataTest {

    @Test
    void binTiersBindBaseCapacityAndConfigBackedStorageWithoutLeakingMutation() throws ReflectiveOperationException {
        BinTier[] tiers = BinTier.values();
        BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE, BaseTier.CREATIVE};
        int[] capacities = {4_096, 8_192, 32_768, 262_144, Integer.MAX_VALUE};
        for (int i = 0; i < tiers.length; i++) {
            assertSame(bases[i], tiers[i].getBaseTier());
            assertEquals(capacities[i], tiers[i].getBaseStorage());
            assertEquals(capacities[i], tiers[i].getStorage());
        }

        Field referenceField = BinTier.class.getDeclaredField("storageReference");
        referenceField.setAccessible(true);
        BinTier mutatedTier = BinTier.BASIC;
        CachedIntValue previous = (CachedIntValue) referenceField.get(mutatedTier);
        TestIntConfig config = new TestIntConfig(12_345, 0);
        CachedIntValue reference = config.first;
        try {
            mutatedTier.setConfigReference(reference);
            assertEquals(12_345, mutatedTier.getStorage());
            reference.set(54_321);
            assertEquals(54_321, reference.get());
            assertEquals(54_321, mutatedTier.getStorage());
        } finally {
            mutatedTier.setConfigReference(previous);
        }
        assertSame(previous, referenceField.get(mutatedTier));
    }

    @Test
    void energyCubeTiersBindCompleteLongMetadataAndRestorableConfigOverrides() throws ReflectiveOperationException {
        EnergyCubeTier[] tiers = EnergyCubeTier.values();
        BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE, BaseTier.CREATIVE};
        long[] capacities = {4_000_000L, 16_000_000L, 64_000_000L, 256_000_000L, Long.MAX_VALUE};
        long[] outputs = {4_000L, 16_000L, 64_000L, 256_000L, Long.MAX_VALUE};
        String[] names = {"basic", "advanced", "elite", "ultimate", "creative"};
        for (int i = 0; i < tiers.length; i++) {
            assertSame(bases[i], tiers[i].getBaseTier());
            assertEquals(capacities[i], tiers[i].getBaseMaxEnergy());
            assertEquals(capacities[i], tiers[i].getMaxEnergy());
            assertEquals(outputs[i], tiers[i].getBaseOutput());
            assertEquals(outputs[i], tiers[i].getOutput());
            assertEquals(names[i], tiers[i].getSerializedName());
        }

        Field storageField = EnergyCubeTier.class.getDeclaredField("storageReference");
        Field outputField = EnergyCubeTier.class.getDeclaredField("outputReference");
        storageField.setAccessible(true);
        outputField.setAccessible(true);
        CachedLongValue previousStorage = (CachedLongValue) storageField.get(EnergyCubeTier.BASIC);
        CachedLongValue previousOutput = (CachedLongValue) outputField.get(EnergyCubeTier.BASIC);
        TestLongConfig config = new TestLongConfig(9_007_199_254_740_993L, 9_007_199_254_740_995L);
        try {
            EnergyCubeTier.BASIC.setConfigReference(config.first, config.second);
            assertEquals(9_007_199_254_740_993L, EnergyCubeTier.BASIC.getMaxEnergy());
            assertEquals(9_007_199_254_740_995L, EnergyCubeTier.BASIC.getOutput());
        } finally {
            EnergyCubeTier.BASIC.setConfigReference(previousStorage, previousOutput);
        }
        assertSame(previousStorage, storageField.get(EnergyCubeTier.BASIC));
        assertSame(previousOutput, outputField.get(EnergyCubeTier.BASIC));
    }

    @Test
    void inductionTiersBindCompleteLongMetadataAndRestorableConfigOverrides() throws ReflectiveOperationException {
        InductionCellTier[] cells = InductionCellTier.values();
        BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE};
        long[] capacities = {8_000_000_000L, 64_000_000_000L, 512_000_000_000L, 4_000_000_000_000L};
        for (int i = 0; i < cells.length; i++) {
            assertSame(bases[i], cells[i].getBaseTier());
            assertEquals(capacities[i], cells[i].getBaseMaxEnergy());
            assertEquals(capacities[i], cells[i].getMaxEnergy());
        }
        InductionProviderTier[] providers = InductionProviderTier.values();
        long[] outputs = {256_000L, 2_048_000L, 16_384_000L, 131_072_000L};
        for (int i = 0; i < providers.length; i++) {
            assertSame(bases[i], providers[i].getBaseTier());
            assertEquals(outputs[i], providers[i].getBaseOutput());
            assertEquals(outputs[i], providers[i].getOutput());
        }

        Field storageField = InductionCellTier.class.getDeclaredField("storageReference");
        Field outputField = InductionProviderTier.class.getDeclaredField("outputReference");
        storageField.setAccessible(true);
        outputField.setAccessible(true);
        CachedLongValue previousStorage = (CachedLongValue) storageField.get(InductionCellTier.BASIC);
        CachedLongValue previousOutput = (CachedLongValue) outputField.get(InductionProviderTier.BASIC);
        TestLongConfig config = new TestLongConfig(9_007_199_254_740_993L, Long.MAX_VALUE - 1);
        try {
            InductionCellTier.BASIC.setConfigReference(config.first);
            InductionProviderTier.BASIC.setConfigReference(config.second);
            assertEquals(9_007_199_254_740_993L, InductionCellTier.BASIC.getMaxEnergy());
            assertEquals(Long.MAX_VALUE - 1, InductionProviderTier.BASIC.getOutput());
        } finally {
            InductionCellTier.BASIC.setConfigReference(previousStorage);
            InductionProviderTier.BASIC.setConfigReference(previousOutput);
        }
        assertSame(previousStorage, storageField.get(InductionCellTier.BASIC));
        assertSame(previousOutput, outputField.get(InductionProviderTier.BASIC));
    }

    @Test
    void factoryTiersBindExactProcessProgressionAndBases() {
        FactoryTier[] tiers = FactoryTier.values();
        BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE};
        int[] processes = {3, 5, 7, 9};
        for (int i = 0; i < tiers.length; i++) {
            assertSame(bases[i], tiers[i].getBaseTier());
            assertEquals(processes[i], tiers[i].processes);
        }
    }

    @Test
    void qioDriveTiersBindExactTypeCountAndBaseTuples() {
        QIODriveTier[] tiers = QIODriveTier.values();
        BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE};
        long[] counts = {16_000L, 128_000L, 1_048_000L, 16_000_000_000L};
        int[] types = {128, 256, 1_024, 8_192};
        for (int i = 0; i < tiers.length; i++) {
            assertSame(bases[i], tiers[i].getBaseTier());
            assertEquals(counts[i], tiers[i].getMaxCount());
            assertEquals(types[i], tiers[i].getMaxTypes());
        }
    }

    @Test
    void cableTiersBindCompleteMetadataMappingsAndLiveRestorableConfig() throws ReflectiveOperationException {
        Field capacityField = privateField(CableTier.class, "capacityReference");
        CableTier mutatedTier = CableTier.ELITE;
        CachedLongValue previousCapacity = (CachedLongValue) capacityField.get(mutatedTier);
        try {
            CableTier[] tiers = CableTier.values();
            assertArrayEquals(new CableTier[]{CableTier.BASIC, CableTier.ADVANCED, CableTier.ELITE, CableTier.ULTIMATE}, tiers);
            BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE};
            long[] capacities = {8_000L, 128_000L, 1_024_000L, 8_192_000L};
            for (int i = 0; i < tiers.length; i++) {
                assertSame(bases[i], tiers[i].getBaseTier());
                assertEquals(capacities[i], tiers[i].getBaseCapacity());
                assertEquals(capacities[i], tiers[i].getCableCapacity());
            }
            assertSame(CableTier.BASIC, CableTier.get(BaseTier.BASIC));
            assertSame(CableTier.ADVANCED, CableTier.get(BaseTier.ADVANCED));
            assertSame(CableTier.ELITE, CableTier.get(BaseTier.ELITE));
            assertSame(CableTier.ULTIMATE, CableTier.get(BaseTier.ULTIMATE));
            assertSame(CableTier.BASIC, CableTier.get(BaseTier.CREATIVE));
            assertSame(CableTier.BASIC, CableTier.get(null));

            TestLongConfig config = new TestLongConfig(9_007_199_254_740_993L, 0L);
            mutatedTier.setConfigReference(config.first);
            assertEquals(9_007_199_254_740_993L, mutatedTier.getCableCapacity());
            config.first.set(-9_007_199_254_740_995L);
            assertEquals(-9_007_199_254_740_995L, mutatedTier.getCableCapacity());
        } finally {
            mutatedTier.setConfigReference(previousCapacity);
        }
        assertSame(previousCapacity, capacityField.get(mutatedTier));
    }

    @Test
    void chemicalTankTiersBindCompleteMetadataNamesAndLiveRestorableConfig() throws ReflectiveOperationException {
        Field storageField = privateField(ChemicalTankTier.class, "storageReference");
        Field outputField = privateField(ChemicalTankTier.class, "outputReference");
        ChemicalTankTier mutatedTier = ChemicalTankTier.ULTIMATE;
        CachedLongValue previousStorage = (CachedLongValue) storageField.get(mutatedTier);
        CachedLongValue previousOutput = (CachedLongValue) outputField.get(mutatedTier);
        try {
            ChemicalTankTier[] tiers = ChemicalTankTier.values();
            assertArrayEquals(new ChemicalTankTier[]{ChemicalTankTier.BASIC, ChemicalTankTier.ADVANCED, ChemicalTankTier.ELITE,
                  ChemicalTankTier.ULTIMATE, ChemicalTankTier.CREATIVE}, tiers);
            BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE, BaseTier.CREATIVE};
            long[] storage = {64_000L, 256_000L, 1_024_000L, 8_192_000L, Long.MAX_VALUE};
            long[] output = {1_000L, 16_000L, 128_000L, 512_000L, 4_611_686_018_427_387_903L};
            String[] names = {"basic", "advanced", "elite", "ultimate", "creative"};
            for (int i = 0; i < tiers.length; i++) {
                assertSame(bases[i], tiers[i].getBaseTier());
                assertEquals(storage[i], tiers[i].getBaseStorage());
                assertEquals(output[i], tiers[i].getBaseOutput());
                assertEquals(storage[i], tiers[i].getStorage());
                assertEquals(output[i], tiers[i].getOutput());
                assertEquals(names[i], tiers[i].getSerializedName());
            }

            TestLongConfig config = new TestLongConfig(9_007_199_254_741_001L, -9_007_199_254_741_003L);
            mutatedTier.setConfigReference(config.first, config.second);
            assertEquals(9_007_199_254_741_001L, mutatedTier.getStorage());
            assertEquals(-9_007_199_254_741_003L, mutatedTier.getOutput());
            config.first.set(-9_007_199_254_741_005L);
            config.second.set(9_007_199_254_741_007L);
            assertEquals(-9_007_199_254_741_005L, mutatedTier.getStorage());
            assertEquals(9_007_199_254_741_007L, mutatedTier.getOutput());
        } finally {
            mutatedTier.setConfigReference(previousStorage, previousOutput);
        }
        assertSame(previousStorage, storageField.get(mutatedTier));
        assertSame(previousOutput, outputField.get(mutatedTier));
    }

    @Test
    void conductorTiersBindCompleteMetadataMappingsAndLiveRestorableConfig() throws ReflectiveOperationException {
        Field conductionField = privateField(ConductorTier.class, "conductionReference");
        Field capacityField = privateField(ConductorTier.class, "capacityReference");
        Field insulationField = privateField(ConductorTier.class, "insulationReference");
        ConductorTier mutatedTier = ConductorTier.ADVANCED;
        CachedDoubleValue previousConduction = (CachedDoubleValue) conductionField.get(mutatedTier);
        CachedDoubleValue previousCapacity = (CachedDoubleValue) capacityField.get(mutatedTier);
        CachedDoubleValue previousInsulation = (CachedDoubleValue) insulationField.get(mutatedTier);
        try {
            ConductorTier[] tiers = ConductorTier.values();
            assertArrayEquals(new ConductorTier[]{ConductorTier.BASIC, ConductorTier.ADVANCED, ConductorTier.ELITE, ConductorTier.ULTIMATE}, tiers);
            BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE};
            double[] conduction = {5D, 5D, 5D, 5D};
            double[] heatCapacity = {1D, 1D, 1D, 1D};
            double[] insulation = {10D, 400D, 8_000D, 100_000D};
            for (int i = 0; i < tiers.length; i++) {
                assertSame(bases[i], tiers[i].getBaseTier());
                assertEquals(conduction[i], tiers[i].getBaseConduction());
                assertEquals(heatCapacity[i], tiers[i].getBaseHeatCapacity());
                assertEquals(insulation[i], tiers[i].getBaseConductionInsulation());
                assertArrayEquals(new int[]{51, 51, 51, 255}, tiers[i].getBaseColor().rgbaArray());
                assertEquals(0x333333FF, tiers[i].getBaseColor().rgba());
                assertEquals(conduction[i], tiers[i].getInverseConduction());
                assertEquals(heatCapacity[i], tiers[i].getHeatCapacity());
                assertEquals(insulation[i], tiers[i].getInverseConductionInsulation());
            }
            assertSame(ConductorTier.BASIC, ConductorTier.get(BaseTier.BASIC));
            assertSame(ConductorTier.ADVANCED, ConductorTier.get(BaseTier.ADVANCED));
            assertSame(ConductorTier.ELITE, ConductorTier.get(BaseTier.ELITE));
            assertSame(ConductorTier.ULTIMATE, ConductorTier.get(BaseTier.ULTIMATE));
            assertSame(ConductorTier.BASIC, ConductorTier.get(BaseTier.CREATIVE));
            assertSame(ConductorTier.BASIC, ConductorTier.get(null));

            TestDoubleConfig config = new TestDoubleConfig(-12_345.625D, 9_876_543.125D, -0.03125D);
            mutatedTier.setConfigReference(config.first, config.second, config.third);
            assertEquals(-12_345.625D, mutatedTier.getInverseConduction());
            assertEquals(9_876_543.125D, mutatedTier.getHeatCapacity());
            assertEquals(-0.03125D, mutatedTier.getInverseConductionInsulation());
            config.first.set(6_543.875D);
            config.second.set(-8_765_432.5D);
            config.third.set(0.0625D);
            assertEquals(6_543.875D, mutatedTier.getInverseConduction());
            assertEquals(-8_765_432.5D, mutatedTier.getHeatCapacity());
            assertEquals(0.0625D, mutatedTier.getInverseConductionInsulation());
        } finally {
            mutatedTier.setConfigReference(previousConduction, previousCapacity, previousInsulation);
        }
        assertSame(previousConduction, conductionField.get(mutatedTier));
        assertSame(previousCapacity, capacityField.get(mutatedTier));
        assertSame(previousInsulation, insulationField.get(mutatedTier));
    }

    @Test
    void fluidTankTiersBindCompleteMetadataAndLiveRestorableConfig() throws ReflectiveOperationException {
        Field storageField = privateField(FluidTankTier.class, "storageReference");
        Field outputField = privateField(FluidTankTier.class, "outputReference");
        FluidTankTier mutatedTier = FluidTankTier.ADVANCED;
        CachedIntValue previousStorage = (CachedIntValue) storageField.get(mutatedTier);
        CachedIntValue previousOutput = (CachedIntValue) outputField.get(mutatedTier);
        try {
            FluidTankTier[] tiers = FluidTankTier.values();
            assertArrayEquals(new FluidTankTier[]{FluidTankTier.BASIC, FluidTankTier.ADVANCED, FluidTankTier.ELITE,
                  FluidTankTier.ULTIMATE, FluidTankTier.CREATIVE}, tiers);
            BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE, BaseTier.CREATIVE};
            int[] storage = {32_000, 64_000, 128_000, 256_000, Integer.MAX_VALUE};
            int[] output = {1_000, 4_000, 16_000, 64_000, 1_073_741_823};
            for (int i = 0; i < tiers.length; i++) {
                assertSame(bases[i], tiers[i].getBaseTier());
                assertEquals(storage[i], tiers[i].getBaseStorage());
                assertEquals(output[i], tiers[i].getBaseOutput());
                assertEquals(storage[i], tiers[i].getStorage());
                assertEquals(output[i], tiers[i].getOutput());
            }

            TestIntConfig config = new TestIntConfig(2_000_000_003, -2_000_000_011);
            mutatedTier.setConfigReference(config.first, config.second);
            assertEquals(2_000_000_003, mutatedTier.getStorage());
            assertEquals(-2_000_000_011, mutatedTier.getOutput());
            config.first.set(-2_000_000_021);
            config.second.set(2_000_000_033);
            assertEquals(-2_000_000_021, mutatedTier.getStorage());
            assertEquals(2_000_000_033, mutatedTier.getOutput());
        } finally {
            mutatedTier.setConfigReference(previousStorage, previousOutput);
        }
        assertSame(previousStorage, storageField.get(mutatedTier));
        assertSame(previousOutput, outputField.get(mutatedTier));
    }

    @Test
    void pipeTiersBindCompleteMetadataMappingsAndLiveRestorableConfig() throws ReflectiveOperationException {
        Field capacityField = privateField(PipeTier.class, "capacityReference");
        Field pullField = privateField(PipeTier.class, "pullReference");
        PipeTier mutatedTier = PipeTier.ULTIMATE;
        CachedIntValue previousCapacity = (CachedIntValue) capacityField.get(mutatedTier);
        CachedIntValue previousPull = (CachedIntValue) pullField.get(mutatedTier);
        try {
            PipeTier[] tiers = PipeTier.values();
            assertArrayEquals(new PipeTier[]{PipeTier.BASIC, PipeTier.ADVANCED, PipeTier.ELITE, PipeTier.ULTIMATE}, tiers);
            BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE};
            int[] capacity = {2_000, 8_000, 32_000, 128_000};
            int[] pull = {250, 1_000, 8_000, 32_000};
            for (int i = 0; i < tiers.length; i++) {
                assertSame(bases[i], tiers[i].getBaseTier());
                assertEquals(capacity[i], tiers[i].getBaseCapacity());
                assertEquals(pull[i], tiers[i].getBasePull());
                assertEquals(capacity[i], tiers[i].getPipeCapacity());
                assertEquals(pull[i], tiers[i].getPipePullAmount());
            }
            assertSame(PipeTier.BASIC, PipeTier.get(BaseTier.BASIC));
            assertSame(PipeTier.ADVANCED, PipeTier.get(BaseTier.ADVANCED));
            assertSame(PipeTier.ELITE, PipeTier.get(BaseTier.ELITE));
            assertSame(PipeTier.ULTIMATE, PipeTier.get(BaseTier.ULTIMATE));
            assertSame(PipeTier.BASIC, PipeTier.get(BaseTier.CREATIVE));
            assertSame(PipeTier.BASIC, PipeTier.get(null));

            TestIntConfig config = new TestIntConfig(2_000_000_039, -2_000_000_051);
            mutatedTier.setConfigReference(config.first, config.second);
            assertEquals(2_000_000_039, mutatedTier.getPipeCapacity());
            assertEquals(-2_000_000_051, mutatedTier.getPipePullAmount());
            config.first.set(-2_000_000_063);
            config.second.set(2_000_000_081);
            assertEquals(-2_000_000_063, mutatedTier.getPipeCapacity());
            assertEquals(2_000_000_081, mutatedTier.getPipePullAmount());
        } finally {
            mutatedTier.setConfigReference(previousCapacity, previousPull);
        }
        assertSame(previousCapacity, capacityField.get(mutatedTier));
        assertSame(previousPull, pullField.get(mutatedTier));
    }

    @Test
    void transporterTiersBindCompleteMetadataMappingsAndLiveRestorableConfig() throws ReflectiveOperationException {
        Field pullField = privateField(TransporterTier.class, "pullReference");
        Field speedField = privateField(TransporterTier.class, "speedReference");
        TransporterTier mutatedTier = TransporterTier.BASIC;
        CachedIntValue previousPull = (CachedIntValue) pullField.get(mutatedTier);
        CachedIntValue previousSpeed = (CachedIntValue) speedField.get(mutatedTier);
        try {
            TransporterTier[] tiers = TransporterTier.values();
            assertArrayEquals(new TransporterTier[]{TransporterTier.BASIC, TransporterTier.ADVANCED,
                  TransporterTier.ELITE, TransporterTier.ULTIMATE}, tiers);
            BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE};
            int[] pull = {1, 16, 32, 64};
            int[] speed = {5, 10, 20, 50};
            for (int i = 0; i < tiers.length; i++) {
                assertSame(bases[i], tiers[i].getBaseTier());
                assertEquals(pull[i], tiers[i].getBasePull());
                assertEquals(speed[i], tiers[i].getBaseSpeed());
                assertEquals(pull[i], tiers[i].getPullAmount());
                assertEquals(speed[i], tiers[i].getSpeed());
            }
            assertSame(TransporterTier.BASIC, TransporterTier.get(BaseTier.BASIC));
            assertSame(TransporterTier.ADVANCED, TransporterTier.get(BaseTier.ADVANCED));
            assertSame(TransporterTier.ELITE, TransporterTier.get(BaseTier.ELITE));
            assertSame(TransporterTier.ULTIMATE, TransporterTier.get(BaseTier.ULTIMATE));
            assertSame(TransporterTier.BASIC, TransporterTier.get(BaseTier.CREATIVE));
            assertSame(TransporterTier.BASIC, TransporterTier.get(null));

            TestIntConfig config = new TestIntConfig(2_000_000_087, -2_000_000_093);
            mutatedTier.setConfigReference(config.first, config.second);
            assertEquals(2_000_000_087, mutatedTier.getPullAmount());
            assertEquals(-2_000_000_093, mutatedTier.getSpeed());
            config.first.set(-2_000_000_107);
            config.second.set(2_000_000_123);
            assertEquals(-2_000_000_107, mutatedTier.getPullAmount());
            assertEquals(2_000_000_123, mutatedTier.getSpeed());
        } finally {
            mutatedTier.setConfigReference(previousPull, previousSpeed);
        }
        assertSame(previousPull, pullField.get(mutatedTier));
        assertSame(previousSpeed, speedField.get(mutatedTier));
    }

    @Test
    void tubeTiersBindCompleteMetadataMappingsAndLiveRestorableConfig() throws ReflectiveOperationException {
        Field capacityField = privateField(TubeTier.class, "capacityReference");
        Field pullField = privateField(TubeTier.class, "pullReference");
        TubeTier mutatedTier = TubeTier.ELITE;
        CachedLongValue previousCapacity = (CachedLongValue) capacityField.get(mutatedTier);
        CachedLongValue previousPull = (CachedLongValue) pullField.get(mutatedTier);
        try {
            TubeTier[] tiers = TubeTier.values();
            assertArrayEquals(new TubeTier[]{TubeTier.BASIC, TubeTier.ADVANCED, TubeTier.ELITE, TubeTier.ULTIMATE}, tiers);
            BaseTier[] bases = {BaseTier.BASIC, BaseTier.ADVANCED, BaseTier.ELITE, BaseTier.ULTIMATE};
            long[] capacity = {4_000L, 16_000L, 256_000L, 1_024_000L};
            long[] pull = {750L, 2_000L, 64_000L, 256_000L};
            for (int i = 0; i < tiers.length; i++) {
                assertSame(bases[i], tiers[i].getBaseTier());
                assertEquals(capacity[i], tiers[i].getBaseCapacity());
                assertEquals(pull[i], tiers[i].getBasePull());
                assertEquals(capacity[i], tiers[i].getTubeCapacity());
                assertEquals(pull[i], tiers[i].getTubePullAmount());
            }
            assertSame(TubeTier.BASIC, TubeTier.get(BaseTier.BASIC));
            assertSame(TubeTier.ADVANCED, TubeTier.get(BaseTier.ADVANCED));
            assertSame(TubeTier.ELITE, TubeTier.get(BaseTier.ELITE));
            assertSame(TubeTier.ULTIMATE, TubeTier.get(BaseTier.ULTIMATE));
            assertSame(TubeTier.BASIC, TubeTier.get(BaseTier.CREATIVE));
            assertSame(TubeTier.BASIC, TubeTier.get(null));

            TestLongConfig config = new TestLongConfig(9_007_199_254_741_009L, -9_007_199_254_741_011L);
            mutatedTier.setConfigReference(config.first, config.second);
            assertEquals(9_007_199_254_741_009L, mutatedTier.getTubeCapacity());
            assertEquals(-9_007_199_254_741_011L, mutatedTier.getTubePullAmount());
            config.first.set(-9_007_199_254_741_013L);
            config.second.set(9_007_199_254_741_015L);
            assertEquals(-9_007_199_254_741_013L, mutatedTier.getTubeCapacity());
            assertEquals(9_007_199_254_741_015L, mutatedTier.getTubePullAmount());
        } finally {
            mutatedTier.setConfigReference(previousCapacity, previousPull);
        }
        assertSame(previousCapacity, capacityField.get(mutatedTier));
        assertSame(previousPull, pullField.get(mutatedTier));
    }

    private static Field privateField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class TestLongConfig extends BaseMekanismConfig {
        private final MekanismConfigSpec spec;
        private final CachedLongValue first;
        private final CachedLongValue second;

        private TestLongConfig(long firstDefault, long secondDefault) {
            MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
            MekanismConfigSpec.ConfigValue<Long> firstValue = builder.defineInRange("first", firstDefault, Long.MIN_VALUE, Long.MAX_VALUE);
            MekanismConfigSpec.ConfigValue<Long> secondValue = builder.defineInRange("second", secondDefault, Long.MIN_VALUE, Long.MAX_VALUE);
            spec = builder.build();
            first = CachedLongValue.wrap(this, firstValue);
            second = CachedLongValue.wrap(this, secondValue);
        }

        @Override public String getFileName() { return "long-tier-test"; }
        @Override public String getTranslation() { return "Long Tier Test"; }
        @Override public MekanismConfigSpec getConfigSpec() { return spec; }
        @Override public MekanismConfigType getConfigType() { return MekanismConfigType.SERVER; }
        @Override public boolean isLoaded() { return true; }
    }

    private static final class TestIntConfig extends BaseMekanismConfig {
        private final MekanismConfigSpec spec;
        private final CachedIntValue first;
        private final CachedIntValue second;

        private TestIntConfig(int firstDefault, int secondDefault) {
            MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
            MekanismConfigSpec.ConfigValue<Integer> firstValue = builder.defineInRange("first", firstDefault, Integer.MIN_VALUE, Integer.MAX_VALUE);
            MekanismConfigSpec.ConfigValue<Integer> secondValue = builder.defineInRange("second", secondDefault, Integer.MIN_VALUE, Integer.MAX_VALUE);
            spec = builder.build();
            first = CachedIntValue.wrap(this, firstValue);
            second = CachedIntValue.wrap(this, secondValue);
        }

        @Override public String getFileName() { return "int-tier-test"; }
        @Override public String getTranslation() { return "Int Tier Test"; }
        @Override public MekanismConfigSpec getConfigSpec() { return spec; }
        @Override public MekanismConfigType getConfigType() { return MekanismConfigType.SERVER; }
        @Override public boolean isLoaded() { return true; }
    }

    private static final class TestDoubleConfig extends BaseMekanismConfig {
        private final MekanismConfigSpec spec;
        private final CachedDoubleValue first;
        private final CachedDoubleValue second;
        private final CachedDoubleValue third;

        private TestDoubleConfig(double firstDefault, double secondDefault, double thirdDefault) {
            MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
            MekanismConfigSpec.ConfigValue<Double> firstValue = builder.defineInRange("first", firstDefault, -Double.MAX_VALUE, Double.MAX_VALUE);
            MekanismConfigSpec.ConfigValue<Double> secondValue = builder.defineInRange("second", secondDefault, -Double.MAX_VALUE, Double.MAX_VALUE);
            MekanismConfigSpec.ConfigValue<Double> thirdValue = builder.defineInRange("third", thirdDefault, -Double.MAX_VALUE, Double.MAX_VALUE);
            spec = builder.build();
            first = CachedDoubleValue.wrap(this, firstValue);
            second = CachedDoubleValue.wrap(this, secondValue);
            third = CachedDoubleValue.wrap(this, thirdValue);
        }

        @Override public String getFileName() { return "double-tier-test"; }
        @Override public String getTranslation() { return "Double Tier Test"; }
        @Override public MekanismConfigSpec getConfigSpec() { return spec; }
        @Override public MekanismConfigType getConfigType() { return MekanismConfigType.SERVER; }
        @Override public boolean isLoaded() { return true; }
    }
}
