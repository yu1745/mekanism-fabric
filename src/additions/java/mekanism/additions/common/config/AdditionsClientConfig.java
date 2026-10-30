package mekanism.additions.common.config;

import mekanism.common.config.BaseMekanismConfig;
import mekanism.common.config.value.CachedBooleanValue;
import mekanism.common.config.MekanismConfigType;
import mekanism.fabric.config.MekanismConfigSpec;

public class AdditionsClientConfig extends BaseMekanismConfig {

    private final MekanismConfigSpec configSpec;

    public final CachedBooleanValue pushToTalk;

    AdditionsClientConfig() {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();

        pushToTalk = CachedBooleanValue.wrap(this, AdditionsConfigTranslations.CLIENT_PUSH_TO_TALK.applyToBuilder(builder)
              .define("pushToTalk", true));

        configSpec = builder.build();
    }

    @Override
    public String getFileName() {
        return "additions-client";
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