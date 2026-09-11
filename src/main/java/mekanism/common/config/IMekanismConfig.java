package mekanism.common.config;

import mekanism.common.config.value.CachedValue;
import mekanism.fabric.config.MekanismConfigSpec;

public interface IMekanismConfig {

    String getFileName();

    String getTranslation();

    MekanismConfigSpec getConfigSpec();

    default boolean isLoaded() {
        return getConfigSpec().isLoaded();
    }

    MekanismConfigType getConfigType();

    void save();

    void clearCache(boolean unloading);

    void addCachedValue(CachedValue<?> configValue);
}
