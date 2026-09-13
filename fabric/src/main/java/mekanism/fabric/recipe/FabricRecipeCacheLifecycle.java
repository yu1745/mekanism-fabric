package mekanism.fabric.recipe;

import mekanism.common.recipe.lookup.cache.RecipeCacheContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side recipe cache lifecycle owner.
 *
 * <p>Binds the actual server {@link net.minecraft.world.item.crafting.RecipeManager} and its matching registry access at {@code SERVER_STARTED} epoch 1. A successful
 * {@code END_DATA_PACK_RELOAD} binds a fresh context with an incremented epoch only after vanilla has replaced the recipes, while a failed reload preserves the
 * context identity, epoch, and cache state. {@code SERVER_STOPPED} clears the server-thread binding. Chemical data-map publication alone never advances the recipe
 * epoch.</p>
 */
public final class FabricRecipeCacheLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricRecipeCache");
    private static boolean initialized;
    private static long serverEpoch;

    private FabricRecipeCacheLifecycle() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            requireServerThread(server);
            serverEpoch = 1;
            //Note: The server's own registry access is published with the manager so synthetic vanilla smelting never needs a process-global fallback
            RecipeCacheContext.bind(server.getRecipeManager(), server.registryAccess(), serverEpoch);
            LOGGER.info("MEKANISM_FABRIC_RECIPE_CACHE_PROOF phase=start epoch={}", serverEpoch);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (!success) {
                //Note: A failed reload retains the previous context identity, epoch, and cache state
                LOGGER.info("MEKANISM_FABRIC_RECIPE_CACHE_PROOF phase=failed_reload retained_epoch={}", serverEpoch);
                return;
            }
            requireServerThread(server);
            serverEpoch++;
            RecipeCacheContext.bind(server.getRecipeManager(), server.registryAccess(), serverEpoch);
            LOGGER.info("MEKANISM_FABRIC_RECIPE_CACHE_PROOF phase=reload epoch={}", serverEpoch);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            requireServerThread(server);
            RecipeCacheContext.clear();
            serverEpoch = 0;
            LOGGER.info("MEKANISM_FABRIC_RECIPE_CACHE_PROOF phase=stop");
        });
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Recipe cache lifecycle must run on the server thread");
        }
    }
}
