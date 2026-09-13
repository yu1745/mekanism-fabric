package mekanism.fabric.recipe;

import mekanism.api.recipes.ingredients.creator.IChemicalIngredientCreator;
import mekanism.api.recipes.ingredients.creator.IChemicalStackIngredientCreator;
import mekanism.api.recipes.ingredients.creator.IFluidStackIngredientCreator;
import mekanism.api.recipes.ingredients.creator.IIngredientCreatorProvider;
import mekanism.api.recipes.ingredients.creator.IItemStackIngredientCreator;
import mekanism.common.recipe.ingredients.creator.ChemicalStackIngredientCreator;
import mekanism.common.recipe.ingredients.creator.FluidStackIngredientCreator;
import mekanism.common.recipe.ingredients.creator.ItemStackIngredientCreator;
import mekanism.common.recipe.ingredients.ChemicalIngredientCreator;

/**
 * Service implementation supplying the four Mekanism ingredient creator singletons.
 *
 * <p>The item/fluid ingredient values and codecs of this port are implemented layer-neutrally on vanilla types, so no Fabric-specific ingredient details live here;
 * the component/cacheability bridge is owned by the API's {@code IngredientCreatorAccess} and the input caches. Registration and freezing of the built-in chemical
 * ingredient types is deliberately <strong>not</strong> triggered from this class: the chemical ingredient codecs consult the creator service during their own
 * initialization, so type registration happens from the mod bootstrap (and from isolated test bootstraps) before recipe decoding instead.</p>
 */
public final class FabricIngredientCreatorProvider implements IIngredientCreatorProvider {

    @Override
    public IItemStackIngredientCreator itemStackIngredientCreator() {
        return ItemStackIngredientCreator.INSTANCE;
    }

    @Override
    public IFluidStackIngredientCreator fluidStackIngredientCreator() {
        return FluidStackIngredientCreator.INSTANCE;
    }

    @Override
    public IChemicalStackIngredientCreator chemicalStackIngredientCreator() {
        return ChemicalStackIngredientCreator.INSTANCE;
    }

    @Override
    public IChemicalIngredientCreator chemicalIngredientCreator() {
        return ChemicalIngredientCreator.INSTANCE;
    }
}
