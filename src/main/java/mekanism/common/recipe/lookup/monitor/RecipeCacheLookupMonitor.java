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
        RecipeCacheContext.Binding binding = validatedBinding();
        long generation = binding == null ? 0 : binding.invalidationGeneration();
        try {
            CachedRecipe<RECIPE> updated = getUpdatedCache(cacheIndex);
            if (!isBindingCurrent(binding, generation)) {
                releaseCachedRecipe();
                return false;
            }
            CachedRecipe<RECIPE> oldCache = cachedRecipe;
            cachedRecipe = updated;
            if (cachedRecipe != oldCache) {
                handler.onCachedRecipeChanged(cachedRecipe, cacheIndex);
            }
            if (!isBindingCurrent(binding, generation)) {
                releaseCachedRecipe();
                return false;
            }
            if (cachedRecipe != null && shouldUnpause) {
                shouldUnpause = false;
                cachedRecipe.unpauseErrors();
            }
            //Callbacks may synchronously clear or rebind; never stamp their new context onto a candidate resolved before them.
            if (!isBindingCurrent(binding, generation)) {
                releaseCachedRecipe();
                return false;
            }
            acknowledgeResolvedBinding(binding, generation);
        } catch (RuntimeException | Error failure) {
            observedIdentity = null;
            throw failure;
        }
        if (cachedRecipe != null) {
            cachedRecipe.process();
            return true;
        }
        return false;
    }

    @Nullable
    @Override
    public CachedRecipe<RECIPE> getUpdatedCache(int cacheIndex) {
        if (!cachedIndexMatches(cacheIndex)) {
            return null;
        }
        RecipeCacheContext.Binding binding = validatedBinding();
        if (binding == null) {
            //The handler is not required to use a canonical cache for lookup, so enforce no-context here rather than relying on it to return null.
            return null;
        }
        long generation = binding.invalidationGeneration();
        CachedRecipe<RECIPE> updated = ICachedRecipeHolder.super.getUpdatedCache(cacheIndex);
        //This method returns a candidate, not an installed cache: a direct caller must not acknowledge an old cachedRecipe field by merely querying.
        return isBindingCurrent(binding, generation) ? updated : null;
    }

    private void releaseCachedRecipe() {
        CachedRecipe<RECIPE> oldCache = cachedRecipe;
        cachedRecipe = null;
        observedIdentity = null;
        observedInvalidationGeneration = 0;
        if (oldCache != null) {
            handler.onCachedRecipeChanged(null, cacheIndex);
        }
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
        RecipeCacheContext.Binding binding = validatedBinding();
        return binding == null || binding.identityToken() != observedIdentity || binding.invalidationGeneration() != observedInvalidationGeneration;
    }

    /**
     * Records the captured, successfully validated resolution stamp. Never reads a later current binding in its place.
     *
     * <p>Only the opaque identity token and the generation number are retained, never the binding itself, so this monitor cannot keep an old manager, registry access,
     * or level graph alive.</p>
     */
    protected final void acknowledgeResolvedBinding(RecipeCacheContext.Binding binding, long generation) {
        observedIdentity = binding.identityToken();
        observedInvalidationGeneration = generation;
    }

    @Nullable
    protected final RecipeCacheContext.Binding validatedBinding() {
        RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
        if (binding != null) {
            Level level = handler.getLevel();
            if (level != null && level.getRecipeManager() != binding.manager()) {
                throw new IllegalStateException("Recipe lookup monitor level is not bound to the active recipe manager (epoch " + binding.epoch() + ")");
            }
        }
        return binding;
    }

    protected final boolean isBindingCurrent(@Nullable RecipeCacheContext.Binding binding, long generation) {
        RecipeCacheContext.Binding current = validatedBinding();
        return binding != null && current == binding && RecipeCacheContext.currentBinding() == binding && binding.invalidationGeneration() == generation;
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
