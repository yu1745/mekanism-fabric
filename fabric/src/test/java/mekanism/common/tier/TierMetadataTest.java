package mekanism.common.tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import mekanism.api.tier.BaseTier;
import mekanism.common.config.BaseMekanismConfig;
import mekanism.common.config.MekanismConfigType;
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
        TestConfig config = new TestConfig(12_345);
        CachedIntValue reference = CachedIntValue.wrap(config, config.value);
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

    private static final class TestConfig extends BaseMekanismConfig {
        private final MekanismConfigSpec spec;
        private final MekanismConfigSpec.ConfigValue<Integer> value;

        private TestConfig(int defaultValue) {
            MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
            value = builder.defineInRange("storage", defaultValue, 0, Integer.MAX_VALUE);
            spec = builder.build();
        }

        @Override
        public String getFileName() {
            return "tier-test";
        }

        @Override
        public String getTranslation() {
            return "Tier Test";
        }

        @Override
        public MekanismConfigSpec getConfigSpec() {
            return spec;
        }

        @Override
        public MekanismConfigType getConfigType() {
            return MekanismConfigType.SERVER;
        }
    }
}
