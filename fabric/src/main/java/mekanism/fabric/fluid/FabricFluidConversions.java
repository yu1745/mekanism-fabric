package mekanism.fabric.fluid;

import mekanism.api.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.world.level.material.Fluid;

/**
 * Pure value and exact-unit conversions at the Mekanism/Fabric fluid boundary.
 * This class makes no storage, transaction, lookup, or lifecycle guarantees.
 */
public final class FabricFluidConversions {

    public static final int MILLIBUCKETS_PER_BUCKET = 1_000;
    public static final long TRANSFER_UNITS_PER_MILLIBUCKET = 81;
    public static final long MAX_EXACT_TRANSFER_UNITS = (long) Integer.MAX_VALUE * TRANSFER_UNITS_PER_MILLIBUCKET;

    static {
        if ((long) MILLIBUCKETS_PER_BUCKET * TRANSFER_UNITS_PER_MILLIBUCKET != FluidConstants.BUCKET) {
            throw new ExceptionInInitializerError("Fabric fluid bucket no longer equals 81,000 transfer units");
        }
    }

    private FabricFluidConversions() {
    }

    /**
     * Converts a non-negative Mekanism amount to Fabric Transfer units without narrowing or rounding.
     */
    public static long millibucketsToTransferUnits(int millibuckets) {
        if (millibuckets < 0) {
            throw new IllegalArgumentException("millibuckets must be non-negative");
        }
        return Math.multiplyExact((long) millibuckets, TRANSFER_UNITS_PER_MILLIBUCKET);
    }

    /**
     * Converts an exactly representable non-negative Fabric amount to Mekanism millibuckets.
     * Sub-millibucket remainders and values outside the positive-int domain are rejected.
     */
    public static int transferUnitsToMillibucketsExact(long transferUnits) {
        if (transferUnits < 0) {
            throw new IllegalArgumentException("transfer units must be non-negative");
        }
        if (transferUnits % TRANSFER_UNITS_PER_MILLIBUCKET != 0) {
            throw new IllegalArgumentException("transfer units must be divisible by 81");
        }
        long millibuckets = transferUnits / TRANSFER_UNITS_PER_MILLIBUCKET;
        if (millibuckets > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("transfer units exceed the FluidStack int domain");
        }
        return (int) millibuckets;
    }

    /**
     * Converts stack identity and components to a Fabric variant. Empty stacks become blank.
     * A flowing fluid is rejected because Fabric would normalize it to its source fluid.
     */
    public static FluidVariant toFabricVariant(FluidStack stack) {
        if (stack.isEmpty()) {
            return FluidVariant.blank();
        }
        Fluid fluid = stack.getFluid();
        FluidVariant variant = FluidVariant.of(fluid, stack.getComponentsPatch());
        if (variant.getFluid() != fluid) {
            throw new IllegalArgumentException("Fabric FluidVariant would normalize the FluidStack fluid identity");
        }
        return variant;
    }

    /**
     * Creates a stack only when the Fabric amount is exactly representable. Zero or a blank
     * variant becomes the canonical empty stack; nonblank values retain fluid and patch identity.
     */
    public static FluidStack toFluidStack(FluidVariant variant, long transferUnits) {
        int millibuckets = transferUnitsToMillibucketsExact(transferUnits);
        if (millibuckets == 0 || variant.isBlank()) {
            return FluidStack.EMPTY;
        }
        FluidStack stack = new FluidStack(variant.getFluid(), millibuckets);
        stack.getComponents().restorePatch(variant.getComponents());
        return stack;
    }
}
