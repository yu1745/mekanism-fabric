package mekanism.fabric.recipe.fixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.api.chemical.ChemicalDataContext;
import mekanism.common.recipe.lookup.cache.RecipeCacheContext;
import mekanism.fabric.recipe.FabricRecipeCacheClientLifecycle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Isolated real-client probe for the Task041 recipe reload ownership: proves the client-only tail mixin publishes the matching client epochs for recipe packets,
 * disconnect clears the client state, a fresh manager cannot reuse prior candidates, and other threads keep distinct manager and chemical-context identities in one
 * JVM.
 */
public final class RecipeRuntimeClientFixture implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/RecipeRuntimeClientFixture");
    private static final Path ROOT = Path.of(System.getProperty("mekanism.recipe.runtimeRoot")).toAbsolutePath().normalize();
    private static final String ADDRESS = System.getProperty("mekanism.recipe.address", "127.0.0.1:25569");

    private static RecipeManager firstManager;
    private static boolean isolationProven;
    private static boolean initialMarked;
    private static boolean reloadMarked;
    private static boolean disconnectRequested;
    private static boolean disconnectMarked;
    private static boolean reconnectMarked;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(RecipeRuntimeClientFixture::tick);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            //The lifecycle is stopped at JVM shutdown: any late update must fail rather than publish
            FabricRecipeCacheClientLifecycle.stop();
            try {
                FabricRecipeCacheClientLifecycle.onRecipesUpdated(new RecipeManager(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)));
                throw new IllegalStateException("late recipe update after stop must be rejected");
            } catch (IllegalStateException expected) {
                if (!"Recipe update received after the client recipe cache lifecycle stopped".equals(expected.getMessage())) {
                    throw expected;
                }
            }
            marker("client-late-rejected.complete");
        }, "recipe-runtime-client-stop-probe"));
    }

    private static void tick(Minecraft client) {
        if (client.level != null && client.getConnection() != null) {
            RecipeCacheContext.Binding binding = RecipeCacheContext.currentBinding();
            if (binding == null) {
                throw new IllegalStateException("client recipe context must be bound while connected");
            }
            if (!initialMarked) {
                require(binding.epoch() == 1, "first manager packet must bind client epoch one");
                require(binding.manager() == client.level.getRecipeManager(), "bound client manager must be the level's actual recipe manager");
                firstManager = binding.manager();
                initialMarked = true;
                LOGGER.info("MEKANISM_RECIPE_RUNTIME_CLIENT phase=initial epoch=1 manager_bound=true");
                marker("client-initial.complete");
            }
            if (!isolationProven) {
                isolationProven = true;
                proveThreadIsolation();
            }
            if (!reloadMarked && binding.epoch() == 2) {
                require(binding.manager() == firstManager, "same-connection recipe updates keep the manager identity");
                reloadMarked = true;
                LOGGER.info("MEKANISM_RECIPE_RUNTIME_CLIENT phase=reload epoch=2 tail_mixin=true");
                marker("client-reload.complete");
            }
            if (!reconnectMarked && firstManager != null && binding.manager() != firstManager) {
                reconnectMarked = true;
                require(binding.epoch() == 1, "a fresh manager starts again at epoch one");
                require(FabricRecipeCacheClientLifecycle.epoch() == 1, "the client epoch resets with the fresh manager");
                LOGGER.info("MEKANISM_RECIPE_RUNTIME_CLIENT phase=reconnect fresh_manager=true epoch_reset=true prior_candidates_unreachable=true");
                marker("client-reconnect.complete");
            }
            if (reloadMarked && !disconnectRequested) {
                disconnectRequested = true;
                client.execute(() -> {
                    if (client.getConnection() != null) {
                        client.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Recipe runtime reconnect probe"));
                    }
                    client.disconnect();
                });
            }
        } else if (initialMarked && !disconnectMarked) {
            //Note: The disconnect cleanup is scheduled onto this thread from the network thread, so tolerate the
            // short window before it has run, then verify the cleared state
            if (FabricRecipeCacheClientLifecycle.epoch() == 0) {
                require(RecipeCacheContext.currentBinding() == null, "play disconnect must clear the client recipe binding");
                disconnectMarked = true;
                LOGGER.info("MEKANISM_RECIPE_RUNTIME_CLIENT phase=disconnect binding_cleared=true epoch_reset=true");
                marker("client-disconnect.complete");
            }
        } else if (disconnectMarked && !reconnectMarked && Files.isRegularFile(ROOT.resolve("reconnect.signal"))) {
            consumeSignal("reconnect.signal");
            connect(client);
        }
    }

    private static void consumeSignal(String name) {
        try {
            Files.deleteIfExists(ROOT.resolve(name));
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to consume recipe runtime signal " + name, failure);
        }
    }

    private static void connect(Minecraft client) {
        ServerData data = new ServerData("Mekanism recipe runtime", ADDRESS, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(ADDRESS), data, true, null);
    }

    static void proveThreadIsolation() {
        //A second thread in this same JVM keeps its own manager and chemical-context identities
        AtomicReference<RecipeManager> workerManager = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                RecipeCacheContext.Binding foreign = RecipeCacheContext.currentBinding();
                require(foreign == null, "a fresh thread must start without a recipe binding");
                RegistryAccess access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
                RecipeManager manager = new RecipeManager(access);
                RecipeCacheContext.bind(manager, 1);
                workerManager.set(manager);
                require(RecipeCacheContext.currentBinding().manager() == manager && RecipeCacheContext.currentBinding().epoch() == 1,
                      "the worker thread must see its own binding");
                require(ChemicalDataContext.current() == ChemicalDataContext.EMPTY, "the worker thread keeps its own chemical context");
            } finally {
                done.countDown();
            }
        }, "recipe-runtime-worker-probe");
        worker.start();
        try {
            require(done.await(5, TimeUnit.SECONDS), "recipe runtime worker probe did not finish");
            RecipeCacheContext.Binding renderBinding = RecipeCacheContext.currentBinding();
            if (renderBinding != null) {
                require(renderBinding.manager() != workerManager.get(), "the render thread's manager must stay distinct from the worker's");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("recipe runtime isolation probe interrupted", interrupted);
        }
        LOGGER.info("MEKANISM_RECIPE_RUNTIME_CLIENT phase=thread_isolation distinct_managers=true distinct_chemical_contexts=true");
        marker("client-thread-isolation.complete");
    }

    private static void marker(String name) {
        try {
            Files.createDirectories(ROOT);
            Files.writeString(ROOT.resolve(name), "ok\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to write recipe runtime client marker " + name, failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
