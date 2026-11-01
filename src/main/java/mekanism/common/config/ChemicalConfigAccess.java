package mekanism.common.config;

import java.util.Objects;
import mekanism.api.chemical.ChemicalDataContext;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;

/** Platform-neutral publication seam for the narrowly owned chemical/config values. */
public final class ChemicalConfigAccess {
    private static volatile CommonConfig common;
    private static volatile GeneralConfig general;
    private static volatile ServerPolicy serverPolicy = ServerPolicy.DEFAULT;

    private ChemicalConfigAccess() {
    }

    public record ServerPolicy(boolean radiationEnabled, double feConversionRate, boolean blacklistForge) {
        public static final ServerPolicy DEFAULT = new ServerPolicy(true, 2.5, false);
        public ServerPolicy {
            if (!Double.isFinite(feConversionRate) || feConversionRate < 0.0001 || feConversionRate > 10_000) {
                throw new IllegalArgumentException("FE conversion rate outside 0.0001..10000");
            }
        }
    }

    public static synchronized void publishCommon(CommonConfig owner) {
        Objects.requireNonNull(owner);
        if (common != null && common != owner) throw new IllegalStateException("Common config already has an owner");
        common = owner;
    }

    public static synchronized void clearCommon(CommonConfig owner) {
        if (common != owner) throw new IllegalStateException("Common config owner mismatch");
        common = null;
    }

    public static void publishServerPolicy(ServerPolicy policy) {
        serverPolicy = Objects.requireNonNull(policy);
        general = null;
    }
    /** Publish the actual loaded owner without initializing common registries in API-only consumers. */
    public static void publishServerPolicy(GeneralConfig owner) { general = Objects.requireNonNull(owner); }
    public static void resetServerPolicy() {
        general = null;
        serverPolicy = ServerPolicy.DEFAULT;
    }
    public static ServerPolicy serverPolicy() {
        GeneralConfig owner = general;
        return owner != null && owner.isLoaded()
              ? new ServerPolicy(owner.radiationEnabled.get(), owner.forgeConversionRate.get(), owner.blacklistForge.get()) : serverPolicy;
    }
    public static EnergyUnit energyUnit() { CommonConfig c = common; return c == null ? EnergyUnit.FORGE_ENERGY : c.energyUnit.getOrDefault(); }
    public static TemperatureUnit temperatureUnit() { CommonConfig c = common; return c == null ? TemperatureUnit.KELVIN : c.tempUnit.getOrDefault(); }
    public static double feConversionRate() {
        GeneralConfig owner = general;
        if (owner != null && owner.isLoaded()) return owner.forgeConversionRate.get();
        ChemicalDataContext context = ChemicalDataContext.current();
        return context.epoch() > 0 ? context.feConversionRate() : serverPolicy.feConversionRate();
    }
    public static boolean forgeEnabled() {
        GeneralConfig owner = general;
        if (owner != null && owner.isLoaded()) return !owner.blacklistForge.get();
        ChemicalDataContext context = ChemicalDataContext.current();
        return context.epoch() > 0 ? !context.blacklistForge() : !serverPolicy.blacklistForge();
    }
    public static boolean radiationEnabled() {
        GeneralConfig owner = general;
        if (owner != null && owner.isLoaded()) return owner.radiationEnabled.get();
        ChemicalDataContext context = ChemicalDataContext.current();
        return context.epoch() > 0 ? context.radiationEnabled() : serverPolicy.radiationEnabled();
    }
}
