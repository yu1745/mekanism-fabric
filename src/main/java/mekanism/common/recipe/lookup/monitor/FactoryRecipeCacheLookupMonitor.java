package mekanism.common.recipe.lookup.monitor;

import mekanism.api.recipes.MekanismRecipe;
import mekanism.common.recipe.lookup.IRecipeLookupHandler;
import org.jetbrains.annotations.NotNull;

public class FactoryRecipeCacheLookupMonitor<RECIPE extends MekanismRecipe<?>> extends RecipeCacheLookupMonitor<RECIPE> {

    private final Runnable setSortingNeeded;

    public FactoryRecipeCacheLookupMonitor(IRecipeLookupHandler<RECIPE> handler, int cacheIndex, Runnable setSortingNeeded) {
        super(handler, cacheIndex);
        this.setSortingNeeded = setSortingNeeded;
    }

    @Override
    public void onChange() {
        super.onChange();
        //Mark that sorting is needed
        setSortingNeeded.run();
    }

    public void updateCachedRecipe(@NotNull RECIPE recipe) {
        cachedRecipe = createNewCachedRecipe(recipe, cacheIndex);
        //Note: While this is probably not strictly needed we clear our cache of knowing we have no recipe
        // so that we can properly re-enter the lookup cycle if needed
        hasNoRecipe = false;
        //Note: This explicit selection is the second state-write entry point and is resolved against the same published binding as an automatic lookup,
        // so acknowledge that observation too. Otherwise the very next update would consider the binding unobserved and immediately discard the selection.
        // Nothing is loaded from saved data here: an explicit selection must not spuriously reload persisted progress or drop the selected recipe.
        acknowledgeResolvedBinding();
    }
}
