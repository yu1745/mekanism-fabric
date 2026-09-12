package mekanism.api.radiation;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import mekanism.api.chemical.ChemicalDataContext;

/** Narrow radiation validation policy; environmental radiation remains outside the Fabric port. */
public final class RadiationValidation {
    public static final double BASELINE = 1.0E-7;
    private static volatile BooleanSupplier enabled = () -> true;

    private RadiationValidation() {
    }

    public static boolean isEnabled() {
        ChemicalDataContext context = ChemicalDataContext.current();
        return context.epoch() > 0 ? context.radiationEnabled() : enabled.getAsBoolean();
    }

    public static void publish(BooleanSupplier policy) {
        enabled = Objects.requireNonNull(policy, "policy");
    }

    public static void reset() {
        enabled = () -> true;
    }
}
