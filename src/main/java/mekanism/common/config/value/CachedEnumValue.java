package mekanism.common.config.value;

import mekanism.common.config.IMekanismConfig;
import mekanism.fabric.config.MekanismConfigSpec.EnumValue;

public class CachedEnumValue<T extends Enum<T>> extends CachedConfigValue<T> {

    private CachedEnumValue(IMekanismConfig config, EnumValue<T> internal) {
        super(config, internal);
    }

    //Note: Ensure that we provide a nice translated name for any enum value based configs we have
    public static <T extends Enum<T>> CachedEnumValue<T> wrap(IMekanismConfig config, EnumValue<T> internal) {
        return new CachedEnumValue<>(config, internal);
    }
}
