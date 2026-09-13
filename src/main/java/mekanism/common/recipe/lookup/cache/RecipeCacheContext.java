package mekanism.common.recipe.lookup.cache;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-bound recipe cache context.
 *
 * <p>Each binding owns the exact {@link RecipeManager} identity it was published for, the matching {@link RegistryAccess} when the publisher knew one, a positive
 * epoch, a fresh identity token created for every successful publication, and thread-confined identity-keyed mutable state for the {@link AbstractInputRecipeCache}
 * instances plus each recipe owner's immutable usable-recipe list. Creating a new binding discards all cached recipe candidates atomically, and {@link #clear()}
 * releases the manager, registry access and recipe references.</p>
 *
 * @since 10.7.12
 */
public final class RecipeCacheContext {

    private static final ThreadLocal<Binding> ACTIVE = new ThreadLocal<>();

    private RecipeCacheContext() {
    }

    /**
     * Binds a fresh context for the given manager and positive epoch on the calling thread.
     *
     * @return the new binding, with a fresh identity token and no cached state.
     *
     * @throws NullPointerException     if the manager is null.
     * @throws IllegalArgumentException if the epoch is not positive.
     */
    public static Binding bind(RecipeManager manager, long epoch) {
        return bind(manager, null, epoch);
    }

    /**
     * Binds a fresh context for the given manager, matching registry access and positive epoch on the calling thread.
     *
     * @param manager        The exact recipe manager this context serves.
     * @param registryAccess The registry access matching that manager, or {@code null} when the publisher has none. Synthetic vanilla smelting needs it.
     * @param epoch          Positive publication epoch.
     *
     * @return the new binding, with a fresh identity token and no cached state.
     *
     * @throws NullPointerException     if the manager is null.
     * @throws IllegalArgumentException if the epoch is not positive.
     */
    public static Binding bind(RecipeManager manager, @Nullable RegistryAccess registryAccess, long epoch) {
        Objects.requireNonNull(manager, "manager");
        if (epoch < 1) {
            throw new IllegalArgumentException("Recipe cache epochs must be positive");
        }
        Binding binding = new Binding(manager, registryAccess, epoch);
        ACTIVE.set(binding);
        return binding;
    }

    /**
     * Gets the binding active on the calling thread, or {@code null} when no context is bound.
     */
    @Nullable
    public static Binding currentBinding() {
        return ACTIVE.get();
    }

    /**
     * Clears the calling thread's binding, releasing the manager, registry access and any cached recipe references.
     */
    public static void clear() {
        ACTIVE.remove();
    }

    /**
     * One exact published recipe context on one thread.
     */
    public static final class Binding {
        private final RecipeManager manager;
        @Nullable
        private final RegistryAccess registryAccess;
        private final long epoch;
        private final Object identityToken = new Object();
        private long invalidationGeneration;
        private final IdentityHashMap<AbstractInputRecipeCache<?>, AbstractInputRecipeCache.CacheState> states = new IdentityHashMap<>();
        private final IdentityHashMap<RecipeCacheSource<?>, List<? extends RecipeHolder<?>>> recipeLists = new IdentityHashMap<>();

        private Binding(RecipeManager manager, @Nullable RegistryAccess registryAccess, long epoch) {
            this.manager = manager;
            this.registryAccess = registryAccess;
            this.epoch = epoch;
        }

        public RecipeManager manager() {
            return manager;
        }

        /**
         * @return The registry access published with this binding, or {@code null} when the publisher had none. Never borrowed from another connection.
         */
        @Nullable
        public RegistryAccess registryAccess() {
            return registryAccess;
        }

        public long epoch() {
            return epoch;
        }

        /**
         * Gets this binding's immutable usable-recipe list for one owner, computing and caching it on first use.
         *
         * <p>The list lives only inside this binding, so a rebind or {@link RecipeCacheContext#clear()} discards it. An empty result is cached like any other, and the
         * owner is keyed by identity so two recipe types can never share a list.</p>
         *
         * @param owner  Identity of the recipe owner (a recipe type) this list belongs to.
         * @param loader Computes the immutable list; only invoked on a miss.
         *
         * @return the immutable list of usable recipes for the owner, possibly empty.
         */
        @NotNull
        @SuppressWarnings("unchecked")
        public <RECIPE extends Recipe<?>> List<RecipeHolder<RECIPE>> recipes(RecipeCacheSource<RECIPE> owner, Supplier<List<RecipeHolder<RECIPE>>> loader) {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(loader, "loader");
            List<?> cached = recipeLists.get(owner);
            if (cached != null) {
                return (List<RecipeHolder<RECIPE>>) cached;
            }
            List<RecipeHolder<RECIPE>> loaded = List.copyOf(Objects.requireNonNull(loader.get(), "recipe loader returned null"));
            recipeLists.put(owner, loaded);
            return loaded;
        }

        /**
         * Drops one owner's cached recipe list from this binding; the input-cache state of the owner is cleared separately.
         *
         * @param owner Identity of the recipe owner whose list to drop.
         *
         * @return {@code true} if a cached list was present.
         */
        public boolean clearRecipes(RecipeCacheSource<?> owner) {
            return recipeLists.remove(owner) != null;
        }

        /**
         * A fresh identity token for every successful publication; no two bindings ever share one.
         *
         * <p>The token is a plain object with no reference back to this binding, so a consumer may retain it as an opaque observation stamp without retaining the
         * manager, registry access, recipe lists, or the binding itself. Rebinding, even to the same manager and epoch, still produces a new token.</p>
         */
        public Object identityToken() {
            return identityToken;
        }

        /**
         * Gets this binding's cache-invalidation generation. It starts at zero for a fresh binding and is advanced once by every full owner clear of
         * {@link MekanismRecipeType#clearCache()}, while the publication identity and epoch stay untouched: an explicit clear is not a successful datapack reload.
         *
         * <p>Individual index or recipe-list clears do not advance it, because they do not invalidate the whole published dataset.</p>
         */
        public long invalidationGeneration() {
            return invalidationGeneration;
        }

        /**
         * Advances this binding's cache-invalidation generation exactly once for one full owner clear. Not a publication: concurrent owner structures remain valid for
         * the new generation and consumers must re-resolve against the still-bound manager.
         */
        public void advanceInvalidationGeneration() {
            invalidationGeneration++;
        }

        AbstractInputRecipeCache.CacheState state(AbstractInputRecipeCache<?> cache) {
            AbstractInputRecipeCache.CacheState state = states.get(cache);
            if (state == null) {
                state = cache.newState();
                states.put(cache, state);
            }
            return state;
        }

        void clearState(AbstractInputRecipeCache<?> cache) {
            states.remove(cache);
        }
    }
}
