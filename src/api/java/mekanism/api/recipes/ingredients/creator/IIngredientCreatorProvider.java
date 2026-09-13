package mekanism.api.recipes.ingredients.creator;

import mekanism.api.annotations.NothingNullByDefault;

/**
 * Layer-neutral service owner for the four Mekanism ingredient creator singletons.
 *
 * <p>The Fabric bootstrap registers exactly one implementation through {@link mekanism.api.MekanismServices}; zero or multiple
 * providers fail with the same ownership discipline as every other Mekanism service. This narrow seam exists so that
 * {@link IngredientCreatorAccess} can delegate creator lookup without admitting the broad {@code IMekanismAccess} facade,
 * whose JEI/EMI and module surfaces remain deferred.</p>
 *
 * @since 10.7.12
 */
@NothingNullByDefault
public interface IIngredientCreatorProvider {

    /**
     * Gets the item stack ingredient creator.
     *
     * @return the singleton item stack ingredient creator.
     */
    IItemStackIngredientCreator itemStackIngredientCreator();

    /**
     * Gets the fluid stack ingredient creator.
     *
     * @return the singleton fluid stack ingredient creator.
     */
    IFluidStackIngredientCreator fluidStackIngredientCreator();

    /**
     * Gets the chemical stack ingredient creator.
     *
     * @return the singleton chemical stack ingredient creator.
     */
    IChemicalStackIngredientCreator chemicalStackIngredientCreator();

    /**
     * Gets the chemical ingredient creator.
     *
     * @return the singleton chemical ingredient creator.
     */
    IChemicalIngredientCreator chemicalIngredientCreator();
}
