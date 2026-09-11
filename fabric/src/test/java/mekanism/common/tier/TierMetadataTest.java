package mekanism.common.tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import mekanism.api.tier.BaseTier;
import mekanism.common.config.BaseMekanismConfig;
import mekanism.common.config.MekanismConfigType;
import mekanism.common.config.value.CachedIntValue;
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
