package mekanism.common.registries;

import mekanism.api.recipes.ingredients.chemical.ChemicalIngredientTypeRegistry;
import mekanism.api.recipes.ingredients.chemical.CompoundChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.DifferenceChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.EmptyChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.IntersectionChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.SingleChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.TagChemicalIngredient;

/**
 * @implNote The Fabric port registers the six built-in chemical ingredient codecs once in official order into the single canonical
 * {@link ChemicalIngredientTypeRegistry} and freezes it before recipe decoding, replacing the deferred codec register. Duplicate IDs, duplicate codec
 * identities, late registration, and missing built-ins all fail.
 *
 * <p>This owner supplies only the exact six codec declarations; the authoritative registry instance is installed by the construction owner, which in
 * production is the root-published, synchronized {@code FabricChemicalIngredientTypeRegistryLifecycle}. A caller that reaches this method before a registry
 * was installed fails loudly rather than registering into a substitute store.</p>
 */
public class MekanismChemicalIngredientTypes {

    private MekanismChemicalIngredientTypes() {
    }

    /** Registers the six built-in ingredient types in official order and freezes them exactly once, before recipe decoding. */
    public static void ensureRegistered() {
        //Note: Idempotent by construction: the API owner rejects late/duplicate registration and re-validates on an already frozen registry.
        if (ChemicalIngredientTypeRegistry.isFrozen()) {
            return;
        }
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.COMPOUND, CompoundChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.DIFFERENCE, DifferenceChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.EMPTY, EmptyChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.INTERSECTION, IntersectionChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.SINGLE, SingleChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.register(ChemicalIngredientTypeRegistry.TAG, TagChemicalIngredient.CODEC);
        ChemicalIngredientTypeRegistry.freeze();
    }
}
