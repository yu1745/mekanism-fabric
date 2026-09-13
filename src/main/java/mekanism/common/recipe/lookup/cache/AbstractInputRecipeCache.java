package mekanism.common.recipe.lookup.cache;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.InputIngredient;
import mekanism.common.recipe.lookup.cache.type.IInputCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Basic helper implementation for {@link IInputRecipeCache} that resolves the thread-bound recipe context and keeps the mutable cache state under the
 * {@link RecipeCacheContext} binding rather than on the shared cache object.
 *
 * @implNote The Fabric port replaces the deferred recipe-type owner with a {@link RecipeCacheSource}: lookups require the calling thread to have a bound context whose
 * exact manager matches any non-null {@link Level} (mismatched managers fail instead of consulting stale global state), null-level calls use the bound manager, and
 * with no bound context no recipes are served. The bound context's registry access is forwarded to the source, and the owner's own recipe-list method is used so
 * ordering, incomplete filtering and synthetic vanilla smelting are shared. Initialized flags, indices, and complex-recipe sets live in the per-binding state.
 */
public abstract class AbstractInputRecipeCache<RECIPE extends MekanismRecipe<?>> implements IInputRecipeCache {

    protected final RecipeCacheSource<RECIPE> source;

    protected AbstractInputRecipeCache(RecipeCacheSource<RECIPE> source) {
        this.source = source;
    }

    @Override
    public void clear() {
        //Note: Clearing the shared shell discards this cache's state from the current binding; new bindings already start empty
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        if (binding != null) {
            binding.clearState(this);
        }
    }

    /**
     * Resolves (and if needed initializes) this cache's state under the thread-bound recipe context.
     *
     * @return the state, or {@code null} if the calling thread has no bound context (in which case no recipes are served).
     *
     * @throws IllegalStateException if the given non-null level's recipe manager is not the exact bound manager.
     */
    @Nullable
    protected final CacheState resolveState(@Nullable Level world) {
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        if (binding == null) {
            return null;
        }
        if (world != null && world.getRecipeManager() != binding.manager()) {
            throw new IllegalStateException("Recipe cache lookup level is not bound to the active recipe manager (epoch " + binding.epoch() + ")");
        }
        CacheState state = binding.state(this);
        if (!state.initialized) {
            state.initialized = true;
            //Note: The owner's own recipe-list method is used so ordering, incomplete filtering and synthetic smelting are shared instead of bypassed
            initCache(source.recipes(binding.manager(), binding.registryAccess()), state);
        }
        return state;
    }

    /**
     * Creates a fresh, empty state instance for a new binding.
     */
    protected abstract CacheState newState();

    /**
     * Initializes the recipe cache based on the given recipes into the given state.
     *
     * @param recipes Recipes to build the cache for.
     * @param state   Mutable per-binding state to populate.
     */
    protected abstract void initCache(List<RecipeHolder<RECIPE>> recipes, CacheState state);

    /**
     * Helper to check if a cache contains a given input, or if not, if the complex recipe fallback set contains a matching recipe.
     */
    protected <INPUT, INGREDIENT extends InputIngredient<INPUT>, CACHE extends IInputCache<INPUT, INGREDIENT, RECIPE>> boolean containsInput(
          INPUT input, Function<RECIPE, INGREDIENT> inputExtractor, CACHE cache, Set<RECIPE> complexRecipes) {
        if (cache.isEmpty(input)) {
            //Don't allow empty inputs
            return false;
        }
        if (cache.contains(input)) {
            return true;
        }
        for (RECIPE recipe : complexRecipes) {
            if (inputExtractor.apply(recipe).testType(input)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper to check if a cache contains a given input grouping, or if not, if the complex recipe fallback set contains a matching recipe. This method is mainly used
     * for purposes of implementing insertion predicates, so it has the following behaviors. This allows it to short circuit in cases where we already know the input is
     * valid (the last case in the below list).
     * <ul>
     * <li>If the first input is empty: This will check if there is a recipe that the second input is valid for.</li>
     * <li>If the first input is not empty but the second input is empty: This will return true.</li>
     * </ul>
     */
    protected <INPUT_1, INGREDIENT_1 extends InputIngredient<INPUT_1>, CACHE_1 extends IInputCache<INPUT_1, INGREDIENT_1, RECIPE>, INPUT_2,
          INGREDIENT_2 extends InputIngredient<INPUT_2>, CACHE_2 extends IInputCache<INPUT_2, INGREDIENT_2, RECIPE>> boolean containsPairing(
          INPUT_1 input1, Function<RECIPE, INGREDIENT_1> input1Extractor, CACHE_1 cache1, Set<RECIPE> complexIngredients1, INPUT_2 input2,
          Function<RECIPE, INGREDIENT_2> input2Extractor, CACHE_2 cache2, Set<RECIPE> complexIngredients2) {
        if (cache1.isEmpty(input1)) {
            //Note: We don't bother checking if 2 is empty here as it will be verified in containsInput
            return containsInput(input2, input2Extractor, cache2, complexIngredients2);
        } else if (cache2.isEmpty(input2)) {
            return true;
        }
        //Note: If cache 1 contains input 1 then we only need to test the type of input 2 as we already know input 1 matches
        for (RECIPE recipe : cache1.getRecipes(input1)) {
            if (input2Extractor.apply(recipe).testType(input2)) {
                return true;
            }
        }
        //Our quick lookup 1 cache does not contain it, check any recipes where the 1 ingredient was complex
        for (RECIPE recipe : complexIngredients1) {
            if (input1Extractor.apply(recipe).testType(input1) && input2Extractor.apply(recipe).testType(input2)) {
                return true;
            }
        }
        return false;
    }

    /** Base of the per-binding mutable state: the initialized flag shared by every cache shape. */
    protected static abstract class CacheState {
        private boolean initialized;
    }
}
