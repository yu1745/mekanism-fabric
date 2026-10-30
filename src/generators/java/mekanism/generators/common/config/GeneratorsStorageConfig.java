package mekanism.generators.common.config;

import mekanism.common.config.BaseMekanismConfig;
import mekanism.common.config.value.CachedLongValue;
import mekanism.common.config.MekanismConfigType;
import mekanism.fabric.config.MekanismConfigSpec;

public class GeneratorsStorageConfig extends BaseMekanismConfig {

    private final MekanismConfigSpec configSpec;

    public final CachedLongValue heatGenerator;
    public final CachedLongValue bioGenerator;
    public final CachedLongValue solarGenerator;
    public final CachedLongValue advancedSolarGenerator;
    public final CachedLongValue windGenerator;

    GeneratorsStorageConfig() {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();

        heatGenerator = CachedLongValue.definedMin(this, builder, GeneratorsConfigTranslations.ENERGY_STORAGE_GENERATOR_HEAT, "heatGenerator",
              160_000L, 1);
        bioGenerator = CachedLongValue.definedMin(this, builder, GeneratorsConfigTranslations.ENERGY_STORAGE_GENERATOR_BIO, "bioGenerator",
              160_000L, 1);
        solarGenerator = CachedLongValue.definedMin(this, builder, GeneratorsConfigTranslations.ENERGY_STORAGE_GENERATOR_SOLAR, "solarGenerator",
              96_000L, 1);
        advancedSolarGenerator = CachedLongValue.definedMin(this, builder, GeneratorsConfigTranslations.ENERGY_STORAGE_GENERATOR_SOLAR_ADVANCED, "advancedSolarGenerator",
              200_000L, 1);
        windGenerator = CachedLongValue.definedMin(this, builder, GeneratorsConfigTranslations.ENERGY_STORAGE_GENERATOR_WIND, "windGenerator",
              200_000L, 1);

        configSpec = builder.build();
    }

    @Override
    public String getFileName() {
        return "generator-storage";
    }

    @Override
    public String getTranslation() {
        return "Storage Config";
    }

    @Override
    public MekanismConfigSpec getConfigSpec() {
        return configSpec;
    }

    @Override
    public MekanismConfigType getConfigType() {
        return MekanismConfigType.SERVER;
    }
}