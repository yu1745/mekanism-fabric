package mekanism.fabric.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mekanism.api.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FabricFluidConversionsTest {

    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sourceWaterLavaComponentsAndBlankRoundTripExactly() {
        for (Fluid fluid : new Fluid[]{Fluids.WATER, Fluids.LAVA}) {
            FluidStack stack = new FluidStack(fluid.builtInRegistryHolder(), 1_000, namedPatch("exact-source"));
            FluidVariant variant = FabricFluidConversions.toFabricVariant(stack);
            assertSame(fluid, variant.getFluid());
            assertEquals(stack.getComponentsPatch(), variant.getComponents());
            assertEquals(Component.literal("exact-source"), variant.getComponentMap().get(DataComponents.CUSTOM_NAME));

            long units = FabricFluidConversions.millibucketsToTransferUnits(stack.getAmount());
            assertEquals(FluidConstants.BUCKET, units);
            FluidStack restored = FabricFluidConversions.toFluidStack(variant, units);
            assertTrue(FluidStack.matches(stack, restored));
            assertSame(fluid, restored.getFluid());
            assertEquals(stack.getComponentsPatch(), restored.getComponentsPatch());
        }

        DataComponentPatch removalPatch = DataComponentPatch.builder().remove(DataComponents.CUSTOM_NAME).build();
        FluidVariant removalVariant = FluidVariant.of(Fluids.WATER, removalPatch);
        assertEquals(removalPatch, removalVariant.getComponents());
        FluidStack removalStack = FabricFluidConversions.toFluidStack(removalVariant, 81);
        assertEquals(removalPatch, removalStack.getComponentsPatch());
        assertEquals(removalVariant, FabricFluidConversions.toFabricVariant(removalStack));

        assertTrue(FabricFluidConversions.toFabricVariant(FluidStack.EMPTY).isBlank());
        assertTrue(FabricFluidConversions.toFabricVariant(new FluidStack(Fluids.EMPTY, 1_000)).isBlank());
        assertTrue(FabricFluidConversions.toFabricVariant(new FluidStack(Fluids.WATER, 0)).isBlank());
        assertSame(FluidStack.EMPTY, FabricFluidConversions.toFluidStack(FluidVariant.blank(), 81));
        assertSame(FluidStack.EMPTY, FabricFluidConversions.toFluidStack(FluidVariant.of(Fluids.WATER), 0));
    }

    @Test
    void flowingFluidNormalizationIsRejectedInsteadOfClaimingExactRoundTrip() {
        assertSame(Fluids.WATER, FluidVariant.of(Fluids.FLOWING_WATER).getFluid());
        assertSame(Fluids.LAVA, FluidVariant.of(Fluids.FLOWING_LAVA).getFluid());

        for (Fluid flowing : new Fluid[]{Fluids.FLOWING_WATER, Fluids.FLOWING_LAVA}) {
            FluidStack stack = new FluidStack(flowing.builtInRegistryHolder(), 50, namedPatch("flowing"));
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                  () -> FabricFluidConversions.toFabricVariant(stack));
            assertEquals("Fabric FluidVariant would normalize the FluidStack fluid identity", failure.getMessage());
            stack.setAmount(0);
            assertTrue(FabricFluidConversions.toFabricVariant(stack).isBlank());
        }
    }

    @Test
    void factorBucketBoundariesMaximumAndEveryInexactOrNegativeAmountFailClosed() {
        assertEquals(1_000, FabricFluidConversions.MILLIBUCKETS_PER_BUCKET);
        assertEquals(81_000, FluidConstants.BUCKET);
        assertEquals(81, FabricFluidConversions.TRANSFER_UNITS_PER_MILLIBUCKET);
        assertEquals(173_946_175_407L, FabricFluidConversions.MAX_EXACT_TRANSFER_UNITS);

        assertEquals(0, FabricFluidConversions.millibucketsToTransferUnits(0));
        assertEquals(81, FabricFluidConversions.millibucketsToTransferUnits(1));
        assertEquals(FluidConstants.BUCKET, FabricFluidConversions.millibucketsToTransferUnits(1_000));
        assertEquals(FabricFluidConversions.MAX_EXACT_TRANSFER_UNITS,
              FabricFluidConversions.millibucketsToTransferUnits(Integer.MAX_VALUE));
        assertEquals(0, FabricFluidConversions.transferUnitsToMillibucketsExact(0));
        assertEquals(1, FabricFluidConversions.transferUnitsToMillibucketsExact(81));
        assertEquals(1_000, FabricFluidConversions.transferUnitsToMillibucketsExact(FluidConstants.BUCKET));
        assertEquals(Integer.MAX_VALUE, FabricFluidConversions.transferUnitsToMillibucketsExact(
              FabricFluidConversions.MAX_EXACT_TRANSFER_UNITS));

        assertEquals("millibuckets must be non-negative", assertThrows(IllegalArgumentException.class,
              () -> FabricFluidConversions.millibucketsToTransferUnits(-1)).getMessage());
        assertEquals("transfer units must be non-negative", assertThrows(IllegalArgumentException.class,
              () -> FabricFluidConversions.transferUnitsToMillibucketsExact(-1)).getMessage());
        for (long subMillibucket = 1; subMillibucket < 81; subMillibucket++) {
            long inexactUnits = subMillibucket;
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                  () -> FabricFluidConversions.transferUnitsToMillibucketsExact(inexactUnits));
            assertEquals("transfer units must be divisible by 81", failure.getMessage());
        }
        assertThrows(IllegalArgumentException.class,
              () -> FabricFluidConversions.transferUnitsToMillibucketsExact(82));
        long firstTooLargeExact = ((long) Integer.MAX_VALUE + 1) * 81;
        assertEquals("transfer units exceed the FluidStack int domain", assertThrows(IllegalArgumentException.class,
              () -> FabricFluidConversions.transferUnitsToMillibucketsExact(firstTooLargeExact)).getMessage());
        assertThrows(IllegalArgumentException.class,
              () -> FabricFluidConversions.transferUnitsToMillibucketsExact(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
              () -> FabricFluidConversions.toFluidStack(FluidVariant.of(Fluids.WATER), 1));
    }

    @Test
    void deterministicLargePropertyLikeRoundTripsNeverRoundOrProduceRemainders() {
        long state = 0x6A09E667F3BCC909L;
        long checksum = 0;
        FluidVariant water = FluidVariant.of(Fluids.WATER, namedPatch("loop"));
        FluidVariant lava = FluidVariant.of(Fluids.LAVA, namedPatch("loop"));
        for (int i = 0; i < 100_000; i++) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            int millibuckets = (int) (state & Integer.MAX_VALUE);
            long units = FabricFluidConversions.millibucketsToTransferUnits(millibuckets);
            assertEquals(0, units % 81);
            assertEquals(millibuckets, FabricFluidConversions.transferUnitsToMillibucketsExact(units));
            assertTrue(units >= 0 && units <= FabricFluidConversions.MAX_EXACT_TRANSFER_UNITS);
            checksum = Long.rotateLeft(checksum ^ units, 9) + millibuckets;

            if (i < 4_096 && millibuckets > 0) {
                FluidVariant variant = (i & 1) == 0 ? water : lava;
                FluidStack restored = FabricFluidConversions.toFluidStack(variant, units);
                assertSame(variant.getFluid(), restored.getFluid());
                assertEquals(millibuckets, restored.getAmount());
                assertEquals(variant.getComponents(), restored.getComponentsPatch());
                FluidVariant repeated = FabricFluidConversions.toFabricVariant(restored);
                assertEquals(variant, repeated);
            }
        }
        assertEquals(7_725_264_165_987_638_512L, checksum);
    }

    private static DataComponentPatch namedPatch(String name) {
        PatchedDataComponentMap components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
        components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return components.asPatch();
    }
}
