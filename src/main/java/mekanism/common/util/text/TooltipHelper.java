package mekanism.common.util.text;

import mekanism.api.text.ITooltipHelper;
import mekanism.common.MekanismLang;
import mekanism.common.config.ChemicalConfigAccess;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.UnitDisplayUtils.RadiationUnit;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.network.chat.Component;

/**
 * @apiNote Do not instantiate this class directly as it will be done via the service loader. Instead, access instances of this via {@link ITooltipHelper#INSTANCE}
 */
public class TooltipHelper implements ITooltipHelper {

    @Override
    public Component getEnergyPerMBDisplayShort(long energy) {
        return MekanismLang.GENERIC_PER_MB.translate(UnitDisplayUtils.getDisplayShort(energy, UnitDisplayUtils.EnergyUnit.getConfigured()));
    }

    @Override
    public Component getRadioactivityDisplayShort(double radioactivity) {
        return UnitDisplayUtils.getDisplayShort(radioactivity, RadiationUnit.SVH, 2);
    }

    @Override
    public Component getTemperatureDisplayShort(double temperature) {
        return UnitDisplayUtils.getDisplayShort(temperature, ChemicalConfigAccess.temperatureUnit(), true);
    }

    @Override
    public String getFormattedNumber(long number) {
        return TextUtils.format(number);
    }

    @Override
    public Component getPercent(double ratio) {
        return TextUtils.getPercent(ratio);
    }
}