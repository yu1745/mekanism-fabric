package mekanism.client.recipe_viewer.jei;

import mekanism.api.fluid.FluidStack;
import mekanism.fabric.fluid.FabricFluidConversions;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.fabric.ingredients.fluids.JeiFluidIngredient;

/**
 * Fabric port delta: the official viewer code targets NeoForge's {@code NeoForgeTypes.FLUID_STACK} and passes
 * the official fluid stack straight through. Fabric JEI instead exposes
 * {@link FabricTypes#FLUID_STACK} whose values are {@link IJeiFluidIngredient} (a {@code FluidVariant} plus an amount in
 * droplets), so every fluid ingredient crosses this adapter.
 */
final class JeiFluidStacks {

    private JeiFluidStacks() {
    }

    static IJeiFluidIngredient toIngredient(FluidStack stack) {
        return new JeiFluidIngredient(FabricFluidConversions.toFabricVariant(stack),
              FabricFluidConversions.millibucketsToTransferUnits(stack.getAmount()));
    }
}
