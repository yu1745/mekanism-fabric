package mekanism.fabric.recipe;

import java.util.Objects;
import mekanism.common.recipe.lookup.cache.RecipeCacheContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only recipe cache lifecycle owner.
 *
 * <p>{@link #onRecipesUpdated(RecipeManager, RegistryAccess)} is invoked by the client-only mixin at the tail of {@code ClientPacketListener.handleUpdateRecipes},
 * after {@code recipeManager.replaceRecipes}, recipe-book rebuilding, and search-tree refresh. It binds on the Minecraft client thread together with the matching
 * connection registry access, so synthetic vanilla smelting works even before a client {@code Level} exists and can never borrow a foreign connection's access. It
 * treats the first packet for a manager as epoch 1, increments the epoch for each later successful update of that manager, and clears on play disconnect and client
 * stop. Late updates after a stop are rejected.</p>
 */
public final class FabricRecipeCacheClientLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricRecipeCacheClient");

    private static boolean initialized;
    private static RecipeManager manager;
    private static long epoch;
    private static boolean stopped;

    private FabricRecipeCacheClientLifecycle() {
    }

    /** Registers the disconnect and stopping cleanup; must only be called from the client bootstrap. */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        //Note: The disconnect event fires on the network thread; the binding is thread-confined, so the cleanup
        // must run on the owning client executor
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> clear("disconnect")));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stop());
    }

    /**
     * Publishes the recipe context for a successfully received recipe update.
     *
     * @param recipeManager  The connection's own recipe manager.
     * @param registryAccess The matching connection registry access, which the connection owns even before a client level exists.
     *
     * @throws IllegalStateException if called off the client thread or after the client has stopped.
     */
    public static synchronized void onRecipesUpdated(RecipeManager recipeManager, RegistryAccess registryAccess) {
        Objects.requireNonNull(recipeManager, "recipeManager");
        Objects.requireNonNull(registryAccess, "registryAccess");
        if (stopped) {
            throw new IllegalStateException("Recipe update received after the client recipe cache lifecycle stopped");
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || !client.isSameThread()) {
            throw new IllegalStateException("Client recipe cache lifecycle must run on the client thread");
        }
        if (recipeManager != manager) {
            //Note: A fresh manager (new connection, new integrated server) starts again at epoch 1 and cannot reuse prior candidates or a foreign connection's access
            manager = recipeManager;
            epoch = 1;
        } else {
            epoch++;
        }
        //Note: The access is always taken from this update's own connection, so a stale access can never outlive its manager
        RecipeCacheContext.bind(manager, registryAccess, epoch);
        LOGGER.info("MEKANISM_FABRIC_RECIPE_CACHE_CLIENT_PROOF phase=update epoch={}", epoch);
    }

    /** Clears the client binding; repeat-safe. */
    public static synchronized void clear(String reason) {
        manager = null;
        epoch = 0;
        RecipeCacheContext.clear();
        LOGGER.info("MEKANISM_FABRIC_RECIPE_CACHE_CLIENT_PROOF phase=clear reason={}", reason);
    }

    /** Marks the lifecycle stopped so late updates fail; repeat-safe. */
    public static synchronized void stop() {
        stopped = true;
        clear("stop");
    }

    /** Test/fixture observation of the current client epoch. */
    public static synchronized long epoch() {
        return epoch;
    }
}
