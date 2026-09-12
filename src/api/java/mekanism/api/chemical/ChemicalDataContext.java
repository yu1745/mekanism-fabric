package mekanism.api.chemical;

import java.util.Map;
import java.util.Objects;
import mekanism.api.datamaps.MekanismDataMapType;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

/** Immutable, owner-thread-bound chemical data-map view. */
public final class ChemicalDataContext {
    public static final ChemicalDataContext EMPTY = new ChemicalDataContext(0, Map.of(), true, 2.5, false);
    private static final ThreadLocal<ChemicalDataContext> ACTIVE = ThreadLocal.withInitial(() -> EMPTY);
    private final long epoch;
    private final Map<MekanismDataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> maps;
    private final boolean radiationEnabled;
    private final double feConversionRate;
    private final boolean blacklistForge;

    public ChemicalDataContext(long epoch, Map<MekanismDataMapType<Chemical, ?>, ? extends Map<ResourceKey<Chemical>, ?>> maps) {
        this(epoch, maps, true, 2.5, false);
    }

    public ChemicalDataContext(long epoch, Map<MekanismDataMapType<Chemical, ?>, ? extends Map<ResourceKey<Chemical>, ?>> maps,
          boolean radiationEnabled, double feConversionRate, boolean blacklistForge) {
        if (epoch < 0) throw new IllegalArgumentException("epoch must be nonnegative");
        if (!Double.isFinite(feConversionRate) || feConversionRate < 0.0001 || feConversionRate > 10_000) {
            throw new IllegalArgumentException("FE conversion rate outside 0.0001..10000");
        }
        this.epoch = epoch;
        this.radiationEnabled = radiationEnabled;
        this.feConversionRate = feConversionRate;
        this.blacklistForge = blacklistForge;
        java.util.IdentityHashMap<MekanismDataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> copy = new java.util.IdentityHashMap<>();
        maps.forEach((type, values) -> copy.put(Objects.requireNonNull(type), Map.copyOf(values)));
        this.maps = java.util.Collections.unmodifiableMap(copy);
    }

    public long epoch() { return epoch; }
    public boolean radiationEnabled() { return radiationEnabled; }
    public double feConversionRate() { return feConversionRate; }
    public boolean blacklistForge() { return blacklistForge; }
    public static ChemicalDataContext current() { return ACTIVE.get(); }
    public static void bind(ChemicalDataContext context) { ACTIVE.set(Objects.requireNonNull(context)); }
    public static void clear() { ACTIVE.remove(); }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(ResourceKey<Chemical> key, MekanismDataMapType<Chemical, T> type) {
        Map<ResourceKey<Chemical>, ?> values = maps.get(type);
        return values == null ? null : (T) values.get(key);
    }

    public Map<MekanismDataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> maps() { return maps; }
}
