package mekanism.common.recipe.lookup.monitor;

import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.recipe.lookup.cache.RecipeCacheContext;
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
        RecipeCacheContext.Binding binding = validatedBinding();
        if (binding == null) {
            throw new IllegalStateException("Factory recipe selection requires a published recipe context");
        }
        long generation = binding.invalidationGeneration();
        CachedRecipe<RECIPE> selected = createNewCachedRecipe(recipe, cacheIndex);
        if (!isBindingCurrent(binding, generation)) {
            throw new IllegalStateException("Recipe context changed during factory cache creation");
        }
        cachedRecipe = selected;
        //An explicit selection joins the same ownership contract, but does not load saved state or issue the automatic changed-cache callback.
        hasNoRecipe = false;
        acknowledgeResolvedBinding(binding, generation);
    }
}
