package mekanism.common.recipe.lookup.cache;

import java.util.List;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.fluid.FluidStack;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.RotaryRecipe;
import mekanism.api.recipes.basic.BasicRotaryRecipe;
import mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess;
import mekanism.common.registries.MekanismChemicals;
import mekanism.test.ChemicalTestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused contracts for {@link RotaryInputRecipeCache}'s per-binding bidirectional state. These drive the real production cache over real {@link BasicRotaryRecipe}
 * instances; the generic binding/thread/epoch matrix is owned by {@link GenericInputCacheContextTest} and is intentionally not repeated here.
 */
class RotaryInputCacheContextTest {

    private static final RecipeType<RotaryRecipe> ROTARY_TYPE = MekanismRecipeTypes.TYPE_ROTARY.value();

    private static RegistryAccess access;
    private static RecipeManager manager;

    @BeforeAll
    static void bootstrap() {
        access = ChemicalTestBootstrap.initialize();
        manager = new RecipeManager(access);
    }

    @AfterEach
    void clearBinding() {
        RecipeCacheContext.clear();
    }

    @Test
    void oneWayDirectionsAreExcludedFromTheOppositeIndexAndBidirectionalRecipesServeBoth() {
        RotaryInputRecipeCache cache = newRecipeCache();
        //A fluid-to-chemical only recipe has no chemical input: the chemical direction must simply not serve it rather than throwing.
        BasicRotaryRecipe fluidOnly = fluidToChemical(1_000, 10);
        BasicRotaryRecipe chemicalOnly = chemicalToFluid(3_000, 2_000);
        BasicRotaryRecipe bidirectional = both(500, 1_500);
        replaceRecipes(holder("fluid_only", fluidOnly), holder("chemical_only", chemicalOnly), holder("both", bidirectional));
        RecipeCacheContext.bind(manager, 1);

        assertSame(fluidOnly, cache.findFirstRecipe(null, water(1_000)));
        assertSame(bidirectional, cache.findFirstRecipe(null, water(500)));
        assertSame(chemicalOnly, cache.findFirstRecipe(null, hydrogen(3_000)));
        assertSame(bidirectional, cache.findFirstRecipe(null, hydrogen(1_500)));
        assertTrue(cache.containsInput(null, water(1_000)));
        assertTrue(cache.containsInput(null, hydrogen(3_000)));
        assertFalse(cache.containsInput(null, new ChemicalStack(MekanismChemicals.OXYGEN, 20)),
              "the chemical direction must not serve the fluid-to-chemical only recipe");
        assertNull(cache.findFirstRecipe(null, new ChemicalStack(MekanismChemicals.OXYGEN, 20)));

        //Rebinding with only the chemical-to-fluid recipe rebuilds both directional indices: the fluid direction goes cold while the chemical one stays served.
        replaceRecipes(holder("chemical_only", chemicalOnly));
        RecipeCacheContext.bind(manager, 2);
        assertNull(cache.findFirstRecipe(null, water(1_000)), "the fluid direction was rebuilt without the removed recipes");
        assertSame(chemicalOnly, cache.findFirstRecipe(null, hydrogen(3_000)));
    }

    @Test
    void indexedLookupIsAmountSensitiveWhileInsertionTypeChecksIgnoreAmount() {
        RotaryInputRecipeCache cache = newRecipeCache();
        BasicRotaryRecipe fluidRecipe = fluidToChemical(1_000, 10);
        BasicRotaryRecipe chemicalRecipe = chemicalToFluid(20, 2_000);
        replaceRecipes(holder("fluid", fluidRecipe), holder("chemical", chemicalRecipe));
        RecipeCacheContext.bind(manager, 1);

        //containsInput is an insertion-type predicate: a smaller stack of the right type is still accepted.
        assertTrue(cache.containsInput(null, water(1)), "a type-only fluid check accepts a smaller amount");
        assertTrue(cache.containsInput(null, hydrogen(1)), "a type-only chemical check accepts a smaller amount");

        //findFirstRecipe processes for real: the amount must be sufficient, and it is the recipe predicate that rejects it.
        assertSame(fluidRecipe, cache.findFirstRecipe(null, water(1_000)));
        assertNull(cache.findFirstRecipe(null, water(999)), "an undersized fluid stack does not process");
        assertSame(chemicalRecipe, cache.findFirstRecipe(null, hydrogen(20)));
        assertNull(cache.findFirstRecipe(null, hydrogen(19)), "an undersized chemical stack does not process");

        //Empty inputs are rejected in both directions before any index consultation.
        assertFalse(cache.containsInput(null, FluidStack.EMPTY));
        assertFalse(cache.containsInput(null, ChemicalStack.EMPTY));
        assertNull(cache.findFirstRecipe(null, FluidStack.EMPTY));
        assertNull(cache.findFirstRecipe(null, ChemicalStack.EMPTY));
    }

    @Test
    void complexIngredientsFallBackThroughTheRecipePredicatePerDirection() {
        RotaryInputRecipeCache cache = newRecipeCache();
        //A component-bearing fluid ingredient has no quick per-fluid key, so it is only reachable through the fluid fallback set.
        FluidStack named = new FluidStack(Fluids.WATER.builtInRegistryHolder(), 1_000, DataComponentPatch.builder()
              .set(DataComponents.CUSTOM_NAME, Component.literal("named"))
              .build());
        BasicRotaryRecipe complex = new BasicRotaryRecipe(IngredientCreatorAccess.fluid().from(named), new ChemicalStack(MekanismChemicals.HYDROGEN, 10));
        BasicRotaryRecipe plain = chemicalToFluid(20, 2_000);
        replaceRecipes(holder("complex", complex), holder("plain", plain));
        RecipeCacheContext.bind(manager, 1);

        assertTrue(cache.containsInput(null, named), "the component ingredient is reachable through the fluid fallback set");
        assertSame(complex, cache.findFirstRecipe(null, named));
        assertFalse(cache.containsInput(null, water(1_000)),
              "a plain water stack lacks the required component and must not match the complex recipe");
        //The complex recipe is fluid-only, so it must never leak into the chemical direction.
        assertFalse(cache.containsInput(null, new ChemicalStack(MekanismChemicals.OXYGEN, 10)));
        assertSame(plain, cache.findFirstRecipe(null, hydrogen(20)));
    }

    @Test
    void clearReleasesBothDirectionalIndicesAndFallbacks() {
        RotaryInputRecipeCache cache = newRecipeCache();
        replaceRecipes(holder("fluid", fluidToChemical(1_000, 10)), holder("chemical", chemicalToFluid(3_000, 2_000)));
        RecipeCacheContext.bind(manager, 1);
        assertTrue(cache.containsInput(null, water(1_000)));
        assertTrue(cache.containsInput(null, hydrogen(3_000)));

        //Swap the manager's contents without rebinding: the bound context keeps serving both old directional indices.
        replaceRecipes(holder("lava_oxygen", new BasicRotaryRecipe(IngredientCreatorAccess.fluid().fromHolder(Fluids.LAVA.builtInRegistryHolder(), 500),
              IngredientCreatorAccess.chemicalStack().from(IngredientCreatorAccess.chemical().of(MekanismChemicals.OXYGEN.get().getAsHolder()), 5),
              new ChemicalStack(MekanismChemicals.OXYGEN, 5), new FluidStack(Fluids.LAVA, 500))));
        assertTrue(cache.containsInput(null, water(1_000)), "before clear the bound context still serves the old fluid index");
        assertTrue(cache.containsInput(null, hydrogen(3_000)), "before clear the bound context still serves the old chemical index");

        //clear() drops this cache's state from the binding: both directions are rebuilt, so both old indices and fallbacks are gone.
        cache.clear();
        assertFalse(cache.containsInput(null, water(1_000)), "the old fluid index is released by clear");
        assertFalse(cache.containsInput(null, hydrogen(3_000)), "the old chemical index is released by clear");
        assertTrue(cache.containsInput(null, new FluidStack(Fluids.LAVA, 500)), "both directions lazily rebuild from the still-bound manager");
        assertTrue(cache.containsInput(null, new ChemicalStack(MekanismChemicals.OXYGEN, 5)));
    }

    @Test
    void bindingReplacementIsolatesBothDirectionalIndices() {
        RotaryInputRecipeCache cache = newRecipeCache();
        replaceRecipes(holder("water_hydrogen", both(1_000, 10)));
        RecipeCacheContext.bind(manager, 1);
        assertTrue(cache.containsInput(null, water(1_000)));
        assertTrue(cache.containsInput(null, hydrogen(10)));

        //A fresh binding with different recipes discards both old indices atomically.
        replaceRecipes(holder("lava_oxygen", new BasicRotaryRecipe(IngredientCreatorAccess.fluid().fromHolder(Fluids.LAVA.builtInRegistryHolder(), 3_000),
              IngredientCreatorAccess.chemicalStack().from(IngredientCreatorAccess.chemical().of(MekanismChemicals.OXYGEN.get().getAsHolder()), 30),
              new ChemicalStack(MekanismChemicals.OXYGEN, 30), new FluidStack(Fluids.LAVA, 3_000))));
        RecipeCacheContext.Binding replacement = RecipeCacheContext.bind(manager, 2);
        assertNull(cache.findFirstRecipe(null, water(1_000)), "the fluid index from the previous binding is gone");
        assertNull(cache.findFirstRecipe(null, hydrogen(10)), "the chemical index from the previous binding is gone");
        assertNotNull(cache.findFirstRecipe(null, new FluidStack(Fluids.LAVA, 3_000)));
        assertNotNull(cache.findFirstRecipe(null, new ChemicalStack(MekanismChemicals.OXYGEN, 30)));

        //With no bound context nothing is served, and the replacement binding is unaffected by clearing it.
        RecipeCacheContext.clear();
        assertNull(cache.findFirstRecipe(null, new FluidStack(Fluids.LAVA, 3_000)));
        assertNull(cache.findFirstRecipe(null, new ChemicalStack(MekanismChemicals.OXYGEN, 30)));
        assertEquals(2, replacement.epoch());
    }

    private static RotaryInputRecipeCache newRecipeCache() {
        return new RotaryInputRecipeCache(RecipeCacheSource.forType(ROTARY_TYPE));
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    private static ChemicalStack hydrogen(int amount) {
        return new ChemicalStack(MekanismChemicals.HYDROGEN, amount);
    }

    private static BasicRotaryRecipe fluidToChemical(int fluidAmount, int chemicalAmount) {
        return new BasicRotaryRecipe(IngredientCreatorAccess.fluid().fromHolder(Fluids.WATER.builtInRegistryHolder(), fluidAmount),
              new ChemicalStack(MekanismChemicals.HYDROGEN, chemicalAmount));
    }

    private static BasicRotaryRecipe chemicalToFluid(int chemicalAmount, int fluidAmount) {
        return new BasicRotaryRecipe(IngredientCreatorAccess.chemicalStack().from(chemical(), chemicalAmount), new FluidStack(Fluids.WATER, fluidAmount));
    }

    /** Fluid-to-chemical and chemical-to-fluid in one recipe, with the two input amounts given explicitly. */
    private static BasicRotaryRecipe both(int fluidIn, int chemicalIn) {
        return new BasicRotaryRecipe(IngredientCreatorAccess.fluid().fromHolder(Fluids.WATER.builtInRegistryHolder(), fluidIn), chemicalIngredient(chemicalIn),
              new ChemicalStack(MekanismChemicals.HYDROGEN, 10), new FluidStack(Fluids.WATER, 1_000));
    }

    private static mekanism.api.recipes.ingredients.ChemicalStackIngredient chemicalIngredient(int amount) {
        return IngredientCreatorAccess.chemicalStack().from(chemical(), amount);
    }

    private static mekanism.api.recipes.ingredients.chemical.ChemicalIngredient chemical() {
        return IngredientCreatorAccess.chemical().of(MekanismChemicals.HYDROGEN.get().getAsHolder());
    }

    private static void replaceRecipes(RecipeHolder<?>... holders) {
        manager.replaceRecipes(List.of(holders));
    }

    private static <RECIPE extends mekanism.api.recipes.MekanismRecipe<?>> RecipeHolder<RECIPE> holder(String path, RECIPE recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("mekanism_test", path), recipe);
    }
}
