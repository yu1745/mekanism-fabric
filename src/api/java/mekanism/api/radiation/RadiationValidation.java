package mekanism.api.radiation;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import mekanism.api.chemical.ChemicalDataContext;

/** Radiation validation policy published by the active world or connection, with an API-only context fallback. */
public final class RadiationValidation {
    public static final double BASELINE = 1.0E-7;
    private static volatile BooleanSupplier enabled;

    private RadiationValidation() {
    }

    public static boolean isEnabled() {
        BooleanSupplier policy = enabled;
        if (policy != null) return policy.getAsBoolean();
        ChemicalDataContext context = ChemicalDataContext.current();
        return context.epoch() == 0 || context.radiationEnabled();
    }

    public static void publish(BooleanSupplier policy) {
        enabled = Objects.requireNonNull(policy, "policy");
    }

    public static void reset() {
        enabled = null;
    }
}
