package mekanism.common.recipe.lookup.cache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.functions.TriPredicate;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.InputIngredient;
import mekanism.common.recipe.lookup.cache.type.IInputCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Basic implementation for {@link IInputRecipeCache} for handling recipes with a single input.
 *
 * @implNote The Fabric port keeps the complex-recipe set and the input cache instance under the {@link RecipeCacheContext} binding state, created fresh per binding
 * from the constructor-supplied cache factory.
 */
public abstract class SingleInputRecipeCache<INPUT, INGREDIENT extends InputIngredient<INPUT>, RECIPE extends MekanismRecipe<?> & Predicate<INPUT>,
      CACHE extends IInputCache<INPUT, INGREDIENT, RECIPE>> extends AbstractInputRecipeCache<RECIPE> {

    private final Function<RECIPE, INGREDIENT> inputExtractor;
    private final Supplier<CACHE> cacheSupplier;

    protected SingleInputRecipeCache(RecipeCacheSource<RECIPE> source, Function<RECIPE, INGREDIENT> inputExtractor, Supplier<CACHE> cacheSupplier) {
        super(source);
        this.inputExtractor = inputExtractor;
        this.cacheSupplier = cacheSupplier;
    }

    @Override
    protected CacheState newState() {
        return new State<>(new HashSet<>(), cacheSupplier.get());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void initCache(List<RecipeHolder<RECIPE>> recipes, CacheState state) {
        State<INPUT, INGREDIENT, RECIPE, CACHE> single = (State<INPUT, INGREDIENT, RECIPE, CACHE>) state;
        for (RecipeHolder<RECIPE> recipeHolder : recipes) {
            RECIPE recipe = recipeHolder.value();
            if (single.cache.mapInputs(recipe, inputExtractor.apply(recipe))) {
                single.complexRecipes.add(recipe);
            }
        }
    }

    /**
     * Checks if there is a matching recipe that has the given input.
     *
     * @param world World.
     * @param input Recipe input.
     *
     * @return {@code true} if there is a match, {@code false} if there isn't.
     */
    public boolean containsInput(@Nullable Level world, INPUT input) {
        State<INPUT, INGREDIENT, RECIPE, CACHE> state = state(world);
        return state != null && containsInput(input, inputExtractor, state.cache, state.complexRecipes);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private State<INPUT, INGREDIENT, RECIPE, CACHE> state(@Nullable Level world) {
        return (State<INPUT, INGREDIENT, RECIPE, CACHE>) resolveState(world);
    }

    /**
     * Finds the first recipe that matches the given input.
     *
     * @param world World.
     * @param input Recipe input.
     *
     * @return Recipe matching the given input, or {@code null} if no recipe matches.
     */
    @Nullable
    public RECIPE findFirstRecipe(@Nullable Level world, INPUT input) {
        State<INPUT, INGREDIENT, RECIPE, CACHE> state = state(world);
        if (state == null || state.cache.isEmpty(input)) {
            //Don't allow empty inputs
            return null;
        }
        RECIPE recipe = findFirstRecipe(input, state.cache.getRecipes(input));
        return recipe == null ? findFirstRecipe(input, state.complexRecipes) : recipe;
    }

    @Nullable
    private RECIPE findFirstRecipe(INPUT input, Iterable<RECIPE> recipes) {
        for (RECIPE recipe : recipes) {
            if (recipe.test(input)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Finds the first recipe that matches the given input type ignoring the size requirement.
     *
     * @param world World.
     * @param input Recipe input.
     *
     * @return Recipe matching the given input, or {@code null} if no recipe matches.
     */
    @Nullable
    public RECIPE findTypeBasedRecipe(@Nullable Level world, INPUT input) {
        State<INPUT, INGREDIENT, RECIPE, CACHE> state = state(world);
        if (state == null || state.cache.isEmpty(input)) {
            //Don't allow empty inputs
            return null;
        }
        RECIPE recipe = state.cache.findFirstRecipe(input, ConstantPredicates.alwaysTrue());
        if (recipe == null) {
            for (RECIPE complexRecipe : state.complexRecipes) {
                if (inputExtractor.apply(complexRecipe).testType(input)) {
                    return complexRecipe;
                }
            }
        }
        return recipe;
    }

    /**
     * Finds the first recipe that matches the given input type ignoring the size requirement and also matches the given recipe predicate.
     *
     * @param world         World.
     * @param input         Recipe input.
     * @param matchCriteria Extra validation criteria to check.
     *
     * @return Recipe matching the given input, or {@code null} if no recipe matches.
     */
    @Nullable
    public <DATA> RECIPE findTypeBasedRecipe(@Nullable Level world, INPUT input, DATA data, TriPredicate<RECIPE, INPUT, DATA> matchCriteria) {
        State<INPUT, INGREDIENT, RECIPE, CACHE> state = state(world);
        if (state == null || state.cache.isEmpty(input)) {
            //Don't allow empty inputs
            return null;
        }
        for (RECIPE recipe : state.cache.getRecipes(input)) {
            if (matchCriteria.test(recipe, input, data)) {
                return recipe;
            }
        }
        for (RECIPE complexRecipe : state.complexRecipes) {
            if (inputExtractor.apply(complexRecipe).testType(input) && matchCriteria.test(complexRecipe, input, data)) {
                return complexRecipe;
            }
        }
        return null;
    }

    /**
     * Finds the first recipe that matches the given input type ignoring the size requirement and also matches the given recipe predicate.
     *
     * @param world         World.
     * @param input         Recipe input.
     * @param matchCriteria Extra validation criteria to check.
     *
     * @return Recipe matching the given input, or {@code null} if no recipe matches.
     */
    @Nullable
    public <DATA_1, DATA_2> RECIPE findTypeBasedRecipe(@Nullable Level world, INPUT input, DATA_1 data1, DATA_2 data2, CheckRecipeType<INPUT, RECIPE, DATA_1, DATA_2> matchCriteria) {
        State<INPUT, INGREDIENT, RECIPE, CACHE> state = state(world);
        if (state == null || state.cache.isEmpty(input)) {
            //Don't allow empty inputs
            return null;
        }
        for (RECIPE recipe : state.cache.getRecipes(input)) {
            if (matchCriteria.testType(recipe, input, data1, data2)) {
                return recipe;
            }
        }
        for (RECIPE complexRecipe : state.complexRecipes) {
            if (inputExtractor.apply(complexRecipe).testType(input) && matchCriteria.testType(complexRecipe, input, data1, data2)) {
                return complexRecipe;
            }
        }
        return null;
    }

    /** Per-binding mutable state for single-input caches. */
    protected static final class State<INPUT, INGREDIENT extends InputIngredient<INPUT>, RECIPE extends MekanismRecipe<?> & Predicate<INPUT>, CACHE extends IInputCache<INPUT, INGREDIENT, RECIPE>>
          extends CacheState {
        private final Set<RECIPE> complexRecipes;
        private final CACHE cache;

        private State(Set<RECIPE> complexRecipes, CACHE cache) {
            this.complexRecipes = complexRecipes;
            this.cache = cache;
        }
    }

    @FunctionalInterface
    public interface CheckRecipeType<INPUT, RECIPE extends MekanismRecipe<?> & Predicate<INPUT>, DATA_1, DATA_2> {

        boolean testType(RECIPE recipe, INPUT input, DATA_1 data1, DATA_2 data2);
    }
}
