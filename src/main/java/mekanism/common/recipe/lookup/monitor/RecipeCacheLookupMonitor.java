package mekanism.common.recipe.lookup.monitor;

import mekanism.api.IContentsListener;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.ICachedRecipeHolder;
import mekanism.api.recipes.cache.ItemStackConstantChemicalToObjectCachedRecipe;
import mekanism.common.recipe.lookup.IRecipeLookupHandler;
import mekanism.common.recipe.lookup.cache.RecipeCacheContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns one handler's cached recipe for one cache index and drives the {@link ICachedRecipeHolder} lookup protocol.
 *
 * @implNote The official NeoForge monitor invalidates through the process-global {@code CommonWorldTickHandler.flushTagAndRecipeCaches} one-tick flag. The Fabric port
 * instead observes the thread-bound {@link RecipeCacheContext} publication directly: it keeps only the opaque publication identity token and the binding-local
 * invalidation generation it last resolved against. A rebind, even to the same manager and epoch, is a fresh identity; an explicit owner clear advances the generation
 * without changing the publication identity or epoch. Both invalidate, and neither retains a binding, manager, or level graph just for identity tracking.
 */
public class RecipeCacheLookupMonitor<RECIPE extends MekanismRecipe<?>> implements ICachedRecipeHolder<RECIPE>, IContentsListener {

    private final IRecipeLookupHandler<RECIPE> handler;
    protected final int cacheIndex;
    protected CachedRecipe<RECIPE> cachedRecipe;
    protected boolean hasNoRecipe;
    protected boolean shouldUnpause;
    /**
     * Publication identity token this monitor last resolved its cache against; {@code null} when it resolved with no published context.
     */
    @Nullable
    private Object observedIdentity;
    /**
     * Binding-local invalidation generation this monitor last resolved against.
     */
    private long observedInvalidationGeneration;

    public RecipeCacheLookupMonitor(IRecipeLookupHandler<RECIPE> handler) {
        this(handler, 0);
    }

    public RecipeCacheLookupMonitor(IRecipeLookupHandler<RECIPE> handler, int cacheIndex) {
        this.handler = handler;
        this.cacheIndex = cacheIndex;
    }

    protected boolean cachedIndexMatches(int cacheIndex) {
        return this.cacheIndex == cacheIndex;
    }

    @Override
    public final void onContentsChanged() {
        handler.onContentsChanged();
        onChange();
    }

    public void onChange() {
        //Mark that we may have a recipe again
        hasNoRecipe = false;
        unpause();
    }

    public void unpause() {
        shouldUnpause = true;
    }

    /**
     * Helper that wraps {@link #updateAndProcess()} inside of a brief check to calculate how much energy actually got used.
     */
    public long updateAndProcess(IEnergyContainer energyContainer) {
        //Copy this so that if it changes we still have the original amount. Don't bother making it a constant though as this way
        // we can then use minusEqual instead of subtract to remove an extra copy call
        long prev = energyContainer.getEnergy();
        if (updateAndProcess()) {
            //Update amount of energy that actually got used, as if we are "near" full we may not have performed our max number of operations
            return Math.max(0, prev - energyContainer.getEnergy());
        }
        //If we don't have a cached recipe so didn't process anything at all just return zero
        return 0L;
    }

    public boolean updateAndProcess() {
        CachedRecipe<RECIPE> oldCache = cachedRecipe;
        cachedRecipe = getUpdatedCache(cacheIndex);
        if (cachedRecipe != oldCache) {
            handler.onCachedRecipeChanged(cachedRecipe, cacheIndex);
        }
        //Note: Only a resolution that actually returned normally may be acknowledged as current, so a failed lookup keeps the previous observation stale
        acknowledgeResolvedBinding();
        if (cachedRecipe != null) {
            if (shouldUnpause) {
                shouldUnpause = false;
                cachedRecipe.unpauseErrors();
            }
            cachedRecipe.process();
            return true;
        }
        return false;
    }

    @Override
    public void loadSavedData(@NotNull CachedRecipe<RECIPE> cached, int cacheIndex) {
        if (cachedIndexMatches(cacheIndex)) {
            ICachedRecipeHolder.super.loadSavedData(cached, cacheIndex);
            if (cached instanceof ItemStackConstantChemicalToObjectCachedRecipe<?, ?> c &&
                handler instanceof IRecipeLookupHandler.ConstantUsageRecipeLookupHandler lookupHandler) {
                c.loadSavedUsageSoFar(lookupHandler.getSavedUsedSoFar(cacheIndex));
            }
        }
    }

    @Override
    public int getSavedOperatingTicks(int cacheIndex) {
        return cachedIndexMatches(cacheIndex) ? handler.getSavedOperatingTicks(cacheIndex) : ICachedRecipeHolder.super.getSavedOperatingTicks(cacheIndex);
    }

    @Nullable
    @Override
    public CachedRecipe<RECIPE> getCachedRecipe(int cacheIndex) {
        return cachedIndexMatches(cacheIndex) ? cachedRecipe : null;
    }

    @Nullable
    @Override
    public RECIPE getRecipe(int cacheIndex) {
        return cachedIndexMatches(cacheIndex) ? handler.getRecipe(cacheIndex) : null;
    }

    @Nullable
    @Override
    public CachedRecipe<RECIPE> createNewCachedRecipe(@NotNull RECIPE recipe, int cacheIndex) {
        return cachedIndexMatches(cacheIndex) ? handler.createNewCachedRecipe(recipe, cacheIndex) : null;
    }

    /**
     * Reports whether the cache must be re-resolved against the currently published binding.
     *
     * <p>This is a pure predicate: it only compares the last acknowledged observation with the current publication and never acknowledges anything itself, so calling
     * it repeatedly, or before {@link ICachedRecipeHolder#getUpdatedCache(int)}, cannot consume an invalidation and let stale state through. With no published context
     * it always reports invalid, which detaches any previously resolved candidate instead of processing it.</p>
     *
     * @throws IllegalStateException if the handler reports a real level whose recipe manager is not the exact bound manager; such a mismatch must fail before a
     *                               still-input-valid cached recipe could bypass the recipe lookup.
     */
    @Override
    public boolean invalidateCache() {
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        if (binding == null) {
            //No published recipe context on this thread: never keep processing a candidate resolved against a released binding
            return true;
        }
        Level level = handler.getLevel();
        if (level != null && level.getRecipeManager() != binding.manager()) {
            //A real level that is not bound to the active recipe manager must fail rather than let a still-valid cached recipe stand in for recipe lookup
            throw new IllegalStateException("Recipe lookup monitor level is not bound to the active recipe manager (epoch " + binding.epoch() + ")");
        }
        return binding.identityToken() != observedIdentity || binding.invalidationGeneration() != observedInvalidationGeneration;
    }

    /**
     * Records the currently published binding as the one this monitor's cache was resolved against.
     *
     * <p>Only the opaque identity token and the generation number are retained, never the binding itself, so this monitor cannot keep an old manager, registry access,
     * or level graph alive.</p>
     */
    protected final void acknowledgeResolvedBinding() {
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        if (binding == null) {
            observedIdentity = null;
            observedInvalidationGeneration = 0L;
        } else {
            observedIdentity = binding.identityToken();
            observedInvalidationGeneration = binding.invalidationGeneration();
        }
    }

    @Override
    public void setHasNoRecipe(int cacheIndex) {
        if (cachedIndexMatches(cacheIndex)) {
            hasNoRecipe = true;
        }
    }

    @Override
    public boolean hasNoRecipe(int cacheIndex) {
        return cachedIndexMatches(cacheIndex) ? hasNoRecipe : ICachedRecipeHolder.super.hasNoRecipe(cacheIndex);
    }
}
