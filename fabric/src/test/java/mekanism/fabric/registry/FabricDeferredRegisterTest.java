package mekanism.fabric.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class FabricDeferredRegisterTest {
    private static final ResourceKey<Registry<String>> TEST_REGISTRY = ResourceKey.createRegistryKey(
          ResourceLocation.fromNamespaceAndPath("test", "values"));

    @Test
    void accessBeforeRegistrationFails() {
        FabricDeferredRegister<String> register = new FabricDeferredRegister<>(TEST_REGISTRY, "test");
        FabricDeferredHolder<String> holder = register.register("value", () -> "created");

        assertThrows(IllegalStateException.class, holder::get);
    }

    @Test
    void holderBindsTheCanonicalRegisteredInstance() {
        FabricDeferredRegister<String> register = new FabricDeferredRegister<>(TEST_REGISTRY, "test");
        String created = new String("created");
        FabricDeferredHolder<String> holder = register.register("value", () -> created);
        AtomicReference<String> registrarValue = new AtomicReference<>();

        register.registerAll((registryKey, key, value) -> {
            registrarValue.set(value);
            return value;
        });

        assertSame(created, registrarValue.get());
        assertSame(registrarValue.get(), holder.get());
        assertEquals(FabricRegistryLifecycle.REGISTERED, register.lifecycle());
    }

    @Test
    void duplicateKeyFails() {
        FabricDeferredRegister<String> register = new FabricDeferredRegister<>(TEST_REGISTRY, "test");
        register.register("value", () -> "first");

        assertThrows(IllegalArgumentException.class,
              () -> register.register("value", () -> "second"));
    }

    @Test
    void duplicateRegisterAllFails() {
        FabricDeferredRegister<String> register = new FabricDeferredRegister<>(TEST_REGISTRY, "test");
        register.register("value", () -> "created");
        register.registerAll((registryKey, key, value) -> value);

        assertThrows(IllegalStateException.class,
              () -> register.registerAll((registryKey, key, value) -> value));
    }

    @Test
    void recursiveEvaluationFailsAndMarksBatchFailed() {
        FabricDeferredRegister<String> register = new FabricDeferredRegister<>(TEST_REGISTRY, "test");
        AtomicReference<FabricDeferredHolder<String>> holder = new AtomicReference<>();
        holder.set(register.register("value", () -> holder.get().get()));

        assertThrows(IllegalStateException.class,
              () -> register.registerAll((registryKey, key, value) -> value));
        assertEquals(FabricRegistryLifecycle.FAILED, register.lifecycle());
    }
}
