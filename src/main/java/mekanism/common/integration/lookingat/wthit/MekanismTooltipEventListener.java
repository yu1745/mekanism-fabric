package mekanism.common.integration.lookingat.wthit;

import mcp.mobius.waila.api.ICommonAccessor;
import mcp.mobius.waila.api.IEventListener;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.data.EnergyData;
import mcp.mobius.waila.api.data.FluidData;

/**
 * Fabric port delta: the official plugin registered this listener inline through the legacy {@code IRegistrar}. It
 * lives in its own class so the client half of {@link MekanismWTHITPlugin} can hand it to {@code IClientRegistrar}.
 */
final class MekanismTooltipEventListener implements IEventListener {

    @Override
    public void onHandleTooltip(ITooltip tooltip, ICommonAccessor accessor, IPluginConfig config) {
        if (tooltip.getLine(MekanismWTHITPlugin.MEK_DATA) != null) {
            //If we have mekanism data then clear out the default energy and fluid data as we handle that ourselves.
            //Note: setting a line adds it if absent, so only clear lines that are actually present.
            if (tooltip.getLine(EnergyData.ID) != null) {
                tooltip.setLine(EnergyData.ID);
            }
            if (tooltip.getLine(FluidData.ID) != null) {
                tooltip.setLine(FluidData.ID);
            }
            //TODO - figure out how to remove the health bar for the robit from WTHIT
        }
    }
}
