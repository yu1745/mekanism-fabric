package mekanism.common.registries;

import mekanism.api.recipes.ingredients.chemical.ChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.ChemicalIngredientTypeRegistry;
import mekanism.api.recipes.ingredients.chemical.CompoundChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.DifferenceChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.EmptyChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.IntersectionChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.SingleChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.TagChemicalIngredient;

/**
 * @implNote The Fabric port registers the six built-in chemical ingredient codecs once in official order and freezes the type registry before recipe decoding,
 * replacing the deferred codec register. Duplicate IDs, duplicate codec identities, late registration, and missing built-ins all fail.
 */
public class MekanismChemicalIngredientTypes {

    private MekanismChemicalIngredientTypes() {
    }

    static {
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.COMPOUND, CompoundChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.DIFFERENCE, DifferenceChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.EMPTY, EmptyChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.INTERSECTION, IntersectionChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.SINGLE, SingleChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.TAG, TagChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.freeze();
    }

    /** Forces the built-in ingredient types to be registered and frozen exactly once, before recipe decoding. */
    public static void ensureRegistered() {
        //Note: Referencing the class runs the static initializer above
    }
}
