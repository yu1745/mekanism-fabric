package mekanism.api.chemical;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import mekanism.api.datamaps.MekanismDataMapType;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable, owner-thread-bound chemical data-map view.
 *
 * <p>Each thread binds at most one {@link Binding}, which pairs the exact immutable context object with a thread-confined derived-value hot cache. Binding a different
 * context installs a fresh cache even when the numeric epochs match, while re-binding the same context instance by identity retains the cache. {@link #clear()} removes
 * the entire binding, and no derived state is ever stored on a canonical {@link Chemical} or in a global map.</p>
 */
public final class ChemicalDataContext {
    public static final ChemicalDataContext EMPTY = new ChemicalDataContext(0, Map.of(), true, 2.5, false);
    private static final ThreadLocal<Binding> ACTIVE = ThreadLocal.withInitial(() -> new Binding(EMPTY));
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
        IdentityHashMap<MekanismDataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> copy = new IdentityHashMap<>();
        maps.forEach((type, values) -> copy.put(Objects.requireNonNull(type), Map.copyOf(values)));
        this.maps = java.util.Collections.unmodifiableMap(copy);
    }

    public long epoch() { return epoch; }
    public boolean radiationEnabled() { return radiationEnabled; }
    public double feConversionRate() { return feConversionRate; }
    public boolean blacklistForge() { return blacklistForge; }
    public static ChemicalDataContext current() { return ACTIVE.get().context; }
    public static void bind(ChemicalDataContext context) {
        Objects.requireNonNull(context);
        Binding current = ACTIVE.get();
        if (current.context != context) {
            //Note: A new binding installs a fresh derived-value cache even when the numeric epoch matches, so no candidate derived under a previous publication can be
            // reused through an equal-epoch context replacement
            ACTIVE.set(new Binding(context));
        }
    }
    public static void clear() { ACTIVE.remove(); }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(ResourceKey<Chemical> key, MekanismDataMapType<Chemical, T> type) {
        Map<ResourceKey<Chemical>, ?> values = maps.get(type);
        return values == null ? null : (T) values.get(key);
    }

    public Map<MekanismDataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> maps() { return maps; }

    /** Gets the binding active on the calling thread, never null; the default binding presents {@link #EMPTY}. */
    static Binding activeBinding() {
        return ACTIVE.get();
    }

    /**
     * Thread-confined pairing of one exact immutable context with its derived-value hot cache.
     */
    public static final class Binding {
        private final ChemicalDataContext context;
        private final IdentityHashMap<Chemical, Chemical.DerivedCacheEntry> derivedCache = new IdentityHashMap<>();

        private Binding(ChemicalDataContext context) {
            this.context = context;
        }

        public ChemicalDataContext context() {
            return context;
        }

        Chemical.DerivedCacheEntry entry(Chemical chemical) {
            return derivedCache.get(chemical);
        }

        void cache(Chemical chemical, Chemical.DerivedCacheEntry entry) {
            derivedCache.put(chemical, entry);
        }
    }
}
