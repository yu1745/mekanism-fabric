package mekanism.api.recipes.vanilla_input;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.fluid.FluidStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * Represents a recipe input that for fluid inputs.
 *
 * @since 10.6.0
 */
@NothingNullByDefault
public interface FluidRecipeInput extends RecipeInput {

    @Override
    default ItemStack getItem(int index) {
        return ItemStack.EMPTY;
    }

    /**
     * Gets the fluid with the given index.
     *
     * @param index Index to lookup.
     *
     * @return Fluid.
     */
    FluidStack getFluid(int index);

    @Override
    default boolean isEmpty() {
        for (int i = 0; i < size(); i++) {
            if (!getFluid(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}