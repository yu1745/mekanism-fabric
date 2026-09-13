package mekanism.common.recipe.lookup.cache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.fluid.FluidStack;
import mekanism.api.recipes.RotaryRecipe;
import mekanism.common.recipe.lookup.cache.type.ChemicalInputCache;
import mekanism.common.recipe.lookup.cache.type.FluidInputCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Similar in concept to {@link SingleInputRecipeCache} except specialized to handle Rotary Condensentrator recipes for the purposes of being able to handle both the gas
 * to fluid and fluid to chemical directions.
 *
 * @implNote The Fabric port keeps both directional input caches and both complex-recipe sets under the {@link RecipeCacheContext} binding state, so a rebind discards
 * every indexed candidate atomically and a thread without a bound context serves no recipes. Direction flags are honored at insertion time: a recipe that cannot convert
 * a given direction is never indexed in, or offered by, that direction's lookup.
 */
public class RotaryInputRecipeCache extends AbstractInputRecipeCache<RotaryRecipe> {

    public RotaryInputRecipeCache(RecipeCacheSource<RotaryRecipe> source) {
        super(source);
    }

    @Override
    protected CacheState newState() {
        return new State();
    }

    @Override
    protected void initCache(List<RecipeHolder<RotaryRecipe>> recipes, CacheState state) {
        State rotary = (State) state;
        for (RecipeHolder<RotaryRecipe> recipeHolder : recipes) {
            RotaryRecipe recipe = recipeHolder.value();
            if (recipe.hasFluidToChemical() && rotary.fluidInputCache.mapInputs(recipe, recipe.getFluidInput())) {
                rotary.complexFluidInputRecipes.add(recipe);
            }
            if (recipe.hasChemicalToFluid() && rotary.chemicalInputCache.mapInputs(recipe, recipe.getChemicalInput())) {
                rotary.complexChemicalInputRecipes.add(recipe);
            }
        }
    }

    @Nullable
    private State state(@Nullable Level world) {
        return (State) resolveState(world);
    }

    /**
     * Checks if there is a matching recipe that has the given fluid input.
     *
     * @param world World.
     * @param input Recipe input.
     *
     * @return {@code true} if there is a match, {@code false} if there isn't.
     */
    public boolean containsInput(@Nullable Level world, FluidStack input) {
        State state = state(world);
        return state != null && containsInput(input, RotaryRecipe::getFluidInput, state.fluidInputCache, state.complexFluidInputRecipes);
    }

    /**
     * Checks if there is a matching recipe that has the given chemical input.
     *
     * @param world World.
     * @param input Recipe input.
     *
     * @return {@code true} if there is a match, {@code false} if there isn't.
     */
    public boolean containsInput(@Nullable Level world, ChemicalStack input) {
        State state = state(world);
        return state != null && containsInput(input, RotaryRecipe::getChemicalInput, state.chemicalInputCache, state.complexChemicalInputRecipes);
    }

    /**
     * Finds the first recipe that matches the given fluid input.
     *
     * @param world World.
     * @param input Recipe input.
     *
     * @return Recipe matching the given fluid input, or {@code null} if no recipe matches.
     */
    @Nullable
    public RotaryRecipe findFirstRecipe(@Nullable Level world, FluidStack input) {
        State state = state(world);
        if (state == null || state.fluidInputCache.isEmpty(input)) {
            //Don't allow empty inputs
            return null;
        }
        RotaryRecipe recipe = findFirstRecipe(input, state.fluidInputCache.getRecipes(input));
        return recipe == null ? findFirstRecipe(input, state.complexFluidInputRecipes) : recipe;
    }

    @Nullable
    private RotaryRecipe findFirstRecipe(FluidStack input, Iterable<RotaryRecipe> recipes) {
        for (RotaryRecipe recipe : recipes) {
            if (recipe.test(input)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Finds the first recipe that matches the given chemical input.
     *
     * @param world World.
     * @param input Recipe input.
     *
     * @return Recipe matching the given chemical input, or {@code null} if no recipe matches.
     */
    @Nullable
    public RotaryRecipe findFirstRecipe(@Nullable Level world, ChemicalStack input) {
        State state = state(world);
        if (state == null || state.chemicalInputCache.isEmpty(input)) {
            //Don't allow empty inputs
            return null;
        }
        RotaryRecipe recipe = findFirstRecipe(input, state.chemicalInputCache.getRecipes(input));
        return recipe == null ? findFirstRecipe(input, state.complexChemicalInputRecipes) : recipe;
    }

    @Nullable
    private RotaryRecipe findFirstRecipe(ChemicalStack input, Iterable<RotaryRecipe> recipes) {
        for (RotaryRecipe recipe : recipes) {
            if (recipe.test(input)) {
                return recipe;
            }
        }
        return null;
    }

    /** Per-binding mutable state for the bidirectional rotary cache: one index and one fallback set per direction. */
    private static final class State extends CacheState {
        private final ChemicalInputCache<RotaryRecipe> chemicalInputCache = new ChemicalInputCache<>();
        private final FluidInputCache<RotaryRecipe> fluidInputCache = new FluidInputCache<>();
        private final Set<RotaryRecipe> complexChemicalInputRecipes = new HashSet<>();
        private final Set<RotaryRecipe> complexFluidInputRecipes = new HashSet<>();
    }
}
