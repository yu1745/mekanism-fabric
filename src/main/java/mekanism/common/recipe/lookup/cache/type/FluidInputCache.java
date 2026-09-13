package mekanism.common.recipe.lookup.cache.type;

import mekanism.api.fluid.FluidStack;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.fluid.FluidIngredient;
import net.minecraft.world.level.material.Fluid;

/**
 * @implNote The Fabric port introspects the layer-neutral {@link FluidIngredient}: its resolved stacks already flatten compound children, tags resolve to their current
 * members exactly as the platform ingredient did, and component predicates are marked complex because quick per-fluid lookup is not valid for them.
 */
public class FluidInputCache<RECIPE extends MekanismRecipe<?>> extends ComponentSensitiveInputCache<Fluid, FluidStack, FluidStackIngredient, RECIPE> {

    public FluidInputCache() {
        super(FLUID_AND_COMPONENTS);
    }

    @Override
    public boolean mapInputs(RECIPE recipe, FluidStackIngredient inputIngredient) {
        return mapIngredient(recipe, inputIngredient.ingredient());
    }

    private boolean mapIngredient(RECIPE recipe, FluidIngredient input) {
        if (input.hasComponentPredicate()) {
            //Special handling for component matching ingredients: there is no valid quick lookup key, so mark it as complex
            return true;
        }
        for (FluidStack fluid : input.getStacks()) {
            if (!fluid.isEmpty()) {
                //Ignore empty stacks as some mods have ingredients that some stacks are empty
                addInputCache(fluid.getFluid(), recipe);
            }
        }
        return false;
    }

    @Override
    protected Fluid createKey(FluidStack stack) {
        return stack.getFluid();
    }

    @Override
    public boolean isEmpty(FluidStack input) {
        return input.isEmpty();
    }

    /** Identity-free fluid and components hash strategy matching the official fluid stack linked set semantics. */
    private static final it.unimi.dsi.fastutil.Hash.Strategy<FluidStack> FLUID_AND_COMPONENTS = new it.unimi.dsi.fastutil.Hash.Strategy<>() {
        @Override
        public int hashCode(FluidStack stack) {
            return stack == null ? 0 : FluidStack.hashFluidAndComponents(stack);
        }

        @Override
        public boolean equals(FluidStack first, FluidStack second) {
            return first == second || first != null && second != null && FluidStack.isSameFluidSameComponents(first, second);
        }
    };
}
