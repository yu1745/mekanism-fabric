package mekanism.common.recipe.lookup.cache;

import java.util.IdentityHashMap;
import java.util.Objects;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-bound recipe cache context.
 *
 * <p>Each binding owns the exact {@link RecipeManager} identity it was published for, a positive epoch, a fresh identity token created for every successful
 * publication, and thread-confined identity-keyed mutable state for the {@link AbstractInputRecipeCache} instances. Creating a new binding discards all cached
 * recipe candidates atomically, and {@link #clear()} releases the manager and recipe references.</p>
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
        Objects.requireNonNull(manager, "manager");
        if (epoch < 1) {
            throw new IllegalArgumentException("Recipe cache epochs must be positive");
        }
        Binding binding = new Binding(manager, epoch);
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
     * Clears the calling thread's binding, releasing the manager and any cached recipe references.
     */
    public static void clear() {
        ACTIVE.remove();
    }

    /**
     * One exact published recipe context on one thread.
     */
    public static final class Binding {
        private final RecipeManager manager;
        private final long epoch;
        private final Object identityToken = new Object();
        private final IdentityHashMap<AbstractInputRecipeCache<?>, AbstractInputRecipeCache.CacheState> states = new IdentityHashMap<>();

        private Binding(RecipeManager manager, long epoch) {
            this.manager = manager;
            this.epoch = epoch;
        }

        public RecipeManager manager() {
            return manager;
        }

        public long epoch() {
            return epoch;
        }

        /** A fresh identity token for every successful publication; no two bindings ever share one. */
        Object identityToken() {
            return identityToken;
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
