package mekanism.common.recipe.lookup.cache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import mekanism.api.functions.TriPredicate;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.InputIngredient;
import mekanism.common.recipe.lookup.cache.type.IInputCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Basic implementation for {@link IInputRecipeCache} for handling recipes with three inputs.
 *
 * @implNote The Fabric port keeps the complex-recipe sets and the input cache instances under the {@link RecipeCacheContext} binding state, created fresh per binding
 * from the constructor-supplied cache factories.
 */
public abstract class TripleInputRecipeCache<INPUT_A, INGREDIENT_A extends InputIngredient<INPUT_A>, INPUT_B, INGREDIENT_B extends InputIngredient<INPUT_B>,
      INPUT_C, INGREDIENT_C extends InputIngredient<INPUT_C>, RECIPE extends MekanismRecipe<?> & TriPredicate<INPUT_A, INPUT_B, INPUT_C>,
      CACHE_A extends IInputCache<INPUT_A, INGREDIENT_A, RECIPE>, CACHE_B extends IInputCache<INPUT_B, INGREDIENT_B, RECIPE>,
      CACHE_C extends IInputCache<INPUT_C, INGREDIENT_C, RECIPE>> extends AbstractInputRecipeCache<RECIPE> {

    private final Function<RECIPE, INGREDIENT_A> inputAExtractor;
    private final Function<RECIPE, INGREDIENT_B> inputBExtractor;
    private final Function<RECIPE, INGREDIENT_C> inputCExtractor;
    private final Supplier<CACHE_A> cacheASupplier;
    private final Supplier<CACHE_B> cacheBSupplier;
    private final Supplier<CACHE_C> cacheCSupplier;

    protected TripleInputRecipeCache(RecipeCacheSource<RECIPE> source, Function<RECIPE, INGREDIENT_A> inputAExtractor, Supplier<CACHE_A> cacheASupplier,
          Function<RECIPE, INGREDIENT_B> inputBExtractor, Supplier<CACHE_B> cacheBSupplier, Function<RECIPE, INGREDIENT_C> inputCExtractor, Supplier<CACHE_C> cacheCSupplier) {
        super(source);
        this.inputAExtractor = inputAExtractor;
        this.inputBExtractor = inputBExtractor;
        this.inputCExtractor = inputCExtractor;
        this.cacheASupplier = cacheASupplier;
        this.cacheBSupplier = cacheBSupplier;
        this.cacheCSupplier = cacheCSupplier;
    }

    @Override
    protected CacheState newState() {
        return new State<>(new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>(), cacheASupplier.get(), cacheBSupplier.get(), cacheCSupplier.get());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void initCache(List<RecipeHolder<RECIPE>> recipes, CacheState state) {
        State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C> tripleState
              = (State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C>) state;
        for (RecipeHolder<RECIPE> recipeHolder : recipes) {
            RECIPE recipe = recipeHolder.value();
            boolean complexA = tripleState.cacheA.mapInputs(recipe, inputAExtractor.apply(recipe));
            boolean complexB = tripleState.cacheB.mapInputs(recipe, inputBExtractor.apply(recipe));
            boolean complexC = tripleState.cacheC.mapInputs(recipe, inputCExtractor.apply(recipe));
            if (complexA) {
                tripleState.complexIngredientA.add(recipe);
            }
            if (complexB) {
                tripleState.complexIngredientB.add(recipe);
            }
            if (complexC) {
                tripleState.complexIngredientC.add(recipe);
            }
            if (complexA || complexB || complexC) {
                tripleState.complexRecipes.add(recipe);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C> state(@Nullable Level world) {
        return (State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C>) resolveState(world);
    }

    /**
     * Checks if there is a matching recipe that has the given input.
     *
     * @param world World.
     * @param input Recipe input.
     *
     * @return {@code true} if there is a match, {@code false} if there isn't.
     */
    public boolean containsInputA(@Nullable Level world, INPUT_A input) {
        State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C> state = state(world);
        return state != null && containsInput(input, inputAExtractor, state.cacheA, state.complexIngredientA);
    }

    /**
     * Checks if there is a matching recipe that has the given input.
     *
     * @param world World.
     * @param input Recipe input.
     *
     * @return {@code true} if there is a match, {@code false} if there isn't.
     */
    public boolean containsInputB(@Nullable Level world, INPUT_B input) {
        State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C> state = state(world);
        return state != null && containsInput(input, inputBExtractor, state.cacheB, state.complexIngredientB);
    }

    /**
     * Checks if there is a matching recipe that has the given input.
     *
     * @param world World.
     * @param input Recipe input.
     *
     * @return {@code true} if there is a match, {@code false} if there isn't.
     */
    public boolean containsInputC(@Nullable Level world, INPUT_C input) {
        State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C> state = state(world);
        return state != null && containsInput(input, inputCExtractor, state.cacheC, state.complexIngredientC);
    }

    /**
     * Checks is there is a matching recipe with the given inputs. This method exists as a helper for insertion predicates and will return true if inputA is not empty and
     * inputB and inputC is empty without doing any extra validation on inputA. If however inputA is not empty and only one of the other two inputs is empty this will do
     * validation on inputA and the non-empty input.
     *
     * @param world  World.
     * @param inputA Recipe input A.
     * @param inputB Recipe input B.
     * @param inputC Recipe input C.
     *
     * @return {@code true} if there is a match or if inputA is not empty and inputB and inputC are both empty.
     *
     * @apiNote If you are trying to insert inputA call this method, otherwise call {@link #containsInputBAC(Level, Object, Object, Object)} or
     * {@link #containsInputCAB(Level, Object, Object, Object)} depending on which input is trying to be inserted.
     */
    public boolean containsInputABC(@Nullable Level world, INPUT_A inputA, INPUT_B inputB, INPUT_C inputC) {
        State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C> state = state(world);
        return state != null && containsGrouping(state, inputA, inputAExtractor, state.cacheA, state.complexIngredientA, inputB, inputBExtractor, state.cacheB, state.complexIngredientB,
              inputC, inputCExtractor, state.cacheC, state.complexIngredientC);
    }

    /**
     * Checks is there is a matching recipe with the given inputs. This method exists as a helper for insertion predicates and will return true if inputB is not empty and
     * inputA and inputC is empty without doing any extra validation on inputB. If however inputB is not empty and only one of the other two inputs is empty this will do
     * validation on inputB and the non-empty input.
     *
     * @param world  World.
     * @param inputA Recipe input A.
     * @param inputB Recipe input B.
     * @param inputC Recipe input C.
     *
     * @return {@code true} if there is a match or if inputB is not empty and inputA and inputC are both empty.
     *
     * @apiNote If you are trying to insert inputB call this method, otherwise call {@link #containsInputABC(Level, Object, Object, Object)} or
     * {@link #containsInputCAB(Level, Object, Object, Object)} depending on which input is trying to be inserted.
     */
    public boolean containsInputBAC(@Nullable Level world, INPUT_A inputA, INPUT_B inputB, INPUT_C inputC) {
        State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C> state = state(world);
        return state != null && containsGrouping(state, inputB, inputBExtractor, state.cacheB, state.complexIngredientB, inputA, inputAExtractor, state.cacheA, state.complexIngredientA,
              inputC, inputCExtractor, state.cacheC, state.complexIngredientC);
    }

    /**
     * Checks is there is a matching recipe with the given inputs. This method exists as a helper for insertion predicates and will return true if inputC is not empty and
     * inputA and inputB is empty without doing any extra validation on inputC. If however inputC is not empty and only one of the other two inputs is empty this will do
     * validation on inputC and the non-empty input.
     *
     * @param world  World.
     * @param inputA Recipe input A.
     * @param inputB Recipe input B.
     * @param inputC Recipe input C.
     *
     * @return {@code true} if there is a match or if inputC is not empty and inputA and inputB are both empty.
     *
     * @apiNote If you are trying to insert inputC call this method, otherwise call {@link #containsInputABC(Level, Object, Object, Object)} or
     * {@link #containsInputBAC(Level, Object, Object, Object)} depending on which input is trying to be inserted.
     */
    public boolean containsInputCAB(@Nullable Level world, INPUT_A inputA, INPUT_B inputB, INPUT_C inputC) {
        State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C> state = state(world);
        return state != null && containsGrouping(state, inputC, inputCExtractor, state.cacheC, state.complexIngredientC, inputA, inputAExtractor, state.cacheA, state.complexIngredientA,
              inputB, inputBExtractor, state.cacheB, state.complexIngredientB);
    }

    /**
     * Helper to check if a cache contains a given input grouping, or if not, if the complex recipe fallback set contains a matching recipe. This method is mainly used
     * for purposes of implementing insertion predicates, so it has the following behaviors. This allows it to short circuit in cases where we already know the input is
     * valid (the last case in the below list).
     * <ul>
     * <li>If only the first input is empty: This will check if input two and three are contained.</li>
     * <li>If only the second input is empty: This will check if input one and three are contained.</li>
     * <li>If only the third input is empty: This will check if input one and two are contained.</li>
     * <li>If only the first and third input is empty: This will check if the second input is contained.</li>
     * <li>If only the first input is not empty: This will return true.</li>
     * </ul>
     */
    private <INPUT_1, INGREDIENT_1 extends InputIngredient<INPUT_1>, CACHE_1 extends IInputCache<INPUT_1, INGREDIENT_1, RECIPE>,
          INPUT_2, INGREDIENT_2 extends InputIngredient<INPUT_2>, CACHE_2 extends IInputCache<INPUT_2, INGREDIENT_2, RECIPE>,
          INPUT_3, INGREDIENT_3 extends InputIngredient<INPUT_3>, CACHE_3 extends IInputCache<INPUT_3, INGREDIENT_3, RECIPE>> boolean containsGrouping(
          State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C> state,
          INPUT_1 input1, Function<RECIPE, INGREDIENT_1> input1Extractor, CACHE_1 cache1, Set<RECIPE> complexIngredients1,
          INPUT_2 input2, Function<RECIPE, INGREDIENT_2> input2Extractor, CACHE_2 cache2, Set<RECIPE> complexIngredients2,
          INPUT_3 input3, Function<RECIPE, INGREDIENT_3> input3Extractor, CACHE_3 cache3, Set<RECIPE> complexIngredients3) {
        if (cache1.isEmpty(input1)) {
            if (cache3.isEmpty(input3)) {
                //If 1 and 3 are empty just check 2. We have this extra check here as containsPairing will always return true
                // if the secondary type is empty, but this is the special case when we don't want that to actually happen
                return containsInput(input2, input2Extractor, cache2, complexIngredients2);
            }
            //Note: We don't bother checking if 2 is empty here as it will be verified in containsPairing
            return containsPairing(input2, input2Extractor, cache2, complexIngredients2, input3, input3Extractor, cache3, complexIngredients3);
        } else if (cache2.isEmpty(input2)) {
            //Note: We don't bother checking if 3 is empty here as it will be verified in containsPairing
            return containsPairing(input1, input1Extractor, cache1, complexIngredients1, input3, input3Extractor, cache3, complexIngredients3);
        } else if (cache3.isEmpty(input3)) {
            return containsPairing(input1, input1Extractor, cache1, complexIngredients1, input2, input2Extractor, cache2, complexIngredients2);
        }
        //Note: If cache 1 contains input 1 then we only need to test the type of input 2 and 3 as we already know input 1 matches
        for (RECIPE recipe : cache1.getRecipes(input1)) {
            if (input2Extractor.apply(recipe).testType(input2) && input3Extractor.apply(recipe).testType(input3)) {
                return true;
            }
        }
        //Our quick lookup 1 cache does not contain it, check any recipes where the 1 ingredient was complex
        for (RECIPE recipe : complexIngredients1) {
            if (input1Extractor.apply(recipe).testType(input1) &&
                input2Extractor.apply(recipe).testType(input2) &&
                input3Extractor.apply(recipe).testType(input3)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the first recipe that matches the given inputs.
     *
     * @param world  World.
     * @param inputA Recipe input A.
     * @param inputB Recipe input B.
     * @param inputC Recipe input C.
     *
     * @return Recipe matching the given inputs, or {@code null} if no recipe matches.
     *
     * @implNote Lookups up the recipe first from the A input map (the fact that it is A is arbitrary and just as well could be B or C).
     */
    @Nullable
    public RECIPE findFirstRecipe(@Nullable Level world, INPUT_A inputA, INPUT_B inputB, INPUT_C inputC) {
        State<INPUT_A, INGREDIENT_A, INPUT_B, INGREDIENT_B, INPUT_C, INGREDIENT_C, RECIPE, CACHE_A, CACHE_B, CACHE_C> state = state(world);
        if (state == null || state.cacheA.isEmpty(inputA) || state.cacheB.isEmpty(inputB)) {
            //Don't allow empty inputs
            return null;
        }
        //Lookup a recipe from the A input map (the fact that it is A is arbitrary, it just as well could be B or C)
        RECIPE recipe = findFirstRecipe(inputA, inputB, inputC, state.cacheA.getRecipes(inputA));
        // if there is no recipe, then check if any of our complex recipes (either a, b, or c being complex) match
        return recipe == null ? findFirstRecipe(inputA, inputB, inputC, state.complexRecipes) : recipe;
    }

    @Nullable
    private RECIPE findFirstRecipe(INPUT_A inputA, INPUT_B inputB, INPUT_C inputC, Iterable<RECIPE> recipes) {
        for (RECIPE recipe : recipes) {
            if (recipe.test(inputA, inputB, inputC)) {
                return recipe;
            }
        }
        return null;
    }

    /** Per-binding mutable state for triple-input caches. */
    protected static final class State<INPUT_A, INGREDIENT_A extends InputIngredient<INPUT_A>, INPUT_B, INGREDIENT_B extends InputIngredient<INPUT_B>,
          INPUT_C, INGREDIENT_C extends InputIngredient<INPUT_C>, RECIPE extends MekanismRecipe<?> & TriPredicate<INPUT_A, INPUT_B, INPUT_C>,
          CACHE_A extends IInputCache<INPUT_A, INGREDIENT_A, RECIPE>, CACHE_B extends IInputCache<INPUT_B, INGREDIENT_B, RECIPE>,
          CACHE_C extends IInputCache<INPUT_C, INGREDIENT_C, RECIPE>> extends CacheState {
        private final Set<RECIPE> complexIngredientA;
        private final Set<RECIPE> complexIngredientB;
        private final Set<RECIPE> complexIngredientC;
        private final Set<RECIPE> complexRecipes;
        private final CACHE_A cacheA;
        private final CACHE_B cacheB;
        private final CACHE_C cacheC;

        private State(Set<RECIPE> complexIngredientA, Set<RECIPE> complexIngredientB, Set<RECIPE> complexIngredientC, Set<RECIPE> complexRecipes,
              CACHE_A cacheA, CACHE_B cacheB, CACHE_C cacheC) {
            this.complexIngredientA = complexIngredientA;
            this.complexIngredientB = complexIngredientB;
            this.complexIngredientC = complexIngredientC;
            this.complexRecipes = complexRecipes;
            this.cacheA = cacheA;
            this.cacheB = cacheB;
            this.cacheC = cacheC;
        }
    }
}
