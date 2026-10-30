package mekanism.tools.common.config;

import mekanism.common.config.BaseMekanismConfig;
import mekanism.common.config.value.CachedBooleanValue;
import mekanism.common.config.MekanismConfigType;
import mekanism.fabric.config.MekanismConfigSpec;

public class ToolsClientConfig extends BaseMekanismConfig {

    private final MekanismConfigSpec configSpec;

    public final CachedBooleanValue displayDurabilityTooltips;

    public ToolsClientConfig() {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();

        this.displayDurabilityTooltips = CachedBooleanValue.wrap(this, ToolsConfigTranslations.CLIENT_DURABILITY_TOOLTIPS.applyToBuilder(builder)
              .define("displayDurabilityTooltips", true));

        configSpec = builder.build();
    }

    @Override
    public String getFileName() {
        return "tools-client";
    }

    @Override
    public String getTranslation() {
        return "Client Config";
    }

    @Override
    public MekanismConfigSpec getConfigSpec() {
        return configSpec;
    }

    @Override
    public MekanismConfigType getConfigType() {
        return MekanismConfigType.CLIENT;
    }
}