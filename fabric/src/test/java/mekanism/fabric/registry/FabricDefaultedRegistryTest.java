package mekanism.fabric.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class FabricDefaultedRegistryTest {
    private static final ResourceKey<Registry<TestValue>> REGISTRY_KEY = ResourceKey.createRegistryKey(id("values"));
    private static final ResourceKey<Registry<TestValue>> OTHER_REGISTRY_KEY = ResourceKey.createRegistryKey(id("other_values"));
    private static final ResourceKey<TestValue> DEFAULT_KEY = ResourceKey.create(REGISTRY_KEY, id("default"));
    private static final ResourceKey<TestValue> OTHER_KEY = ResourceKey.create(REGISTRY_KEY, id("other"));

    @Test
    void canonicalDefaultDeferredReferenceFallbackAndFreezeContract() {
        FabricDefaultedRegistry<TestValue> registry = registry();
        assertThrows(IllegalStateException.class, registry::canonicalDefaultHolder);
        FabricDeferredRegister<TestValue> deferred = new FabricDeferredRegister<>(REGISTRY_KEY, "registry_test");
        FabricDeferredHolder<TestValue> defaultHolder = deferred.register("default", () -> new TestValue(registry, "default"));
        FabricDeferredHolder<TestValue> otherHolder = deferred.register("other", () -> new TestValue(registry, "other"));

        deferred.registerAll(registry);

        assertEquals(FabricRegistryLifecycle.REGISTERED, deferred.lifecycle());
        assertSame(defaultHolder.get().intrusiveHolder, defaultHolder.canonicalHolder());
        assertSame(otherHolder.get().intrusiveHolder, otherHolder.canonicalHolder());
        assertSame(defaultHolder.canonicalHolder(), registry.canonicalDefaultHolder());
        assertSame(defaultHolder.canonicalHolder(), registry.getHolderOrThrow(DEFAULT_KEY));
        assertSame(defaultHolder.get(), registry.get(id("missing")));
        assertSame(defaultHolder.get(), registry.byId(-1));
        assertTrue(registry.getOptional(id("missing")).isEmpty());

        assertSame(registry, registry.freeze());
        assertEquals(FabricCustomRegistryLifecycle.FROZEN, registry.lifecycle());
        assertSame(registry, registry.freeze());
        assertThrows(IllegalStateException.class, () -> registry.register(OTHER_KEY, new TestValue("late"), RegistrationInfo.BUILT_IN));
        assertThrows(IllegalStateException.class, () -> registry.createIntrusiveHolder(new TestValue("late")));
        assertThrows(IllegalStateException.class, registry::registerRoot);
    }

    @Test
    void invalidOrIncompleteDefaultsAndSideEffectingVanillaFailuresAreBounded() {
        ResourceKey<TestValue> wrongDefault = ResourceKey.create(OTHER_REGISTRY_KEY, id("default"));
        assertThrows(IllegalArgumentException.class,
              () -> FabricDefaultedRegistry.create(REGISTRY_KEY, wrongDefault, false));

        FabricDefaultedRegistry<TestValue> missingDefault = registry();
        assertThrows(IllegalStateException.class, missingDefault::freeze);
        assertEquals(FabricCustomRegistryLifecycle.CREATED, missingDefault.lifecycle());
        register(missingDefault, DEFAULT_KEY, new TestValue(missingDefault, "recovered_default"));
        assertSame(missingDefault, missingDefault.freeze());

        FabricDefaultedRegistry<TestValue> missingIntrusiveHolder = registry();
        assertThrows(AssertionError.class,
              () -> missingIntrusiveHolder.register(DEFAULT_KEY, new TestValue("no_holder"), RegistrationInfo.BUILT_IN));
        assertEquals(FabricCustomRegistryLifecycle.FAILED, missingIntrusiveHolder.lifecycle());
        assertThrows(IllegalStateException.class, missingIntrusiveHolder::freeze);
        assertThrows(IllegalStateException.class,
              () -> missingIntrusiveHolder.createIntrusiveHolder(new TestValue("terminal")));

        FabricDefaultedRegistry<TestValue> danglingRegistry = registry();
        register(danglingRegistry, DEFAULT_KEY, new TestValue(danglingRegistry, "default"));
        TestValue dangling = new TestValue(danglingRegistry, "dangling");
        assertFalse(dangling.intrusiveHolder.isBound());
        assertThrows(IllegalStateException.class, danglingRegistry::freeze);
        assertEquals(FabricCustomRegistryLifecycle.FAILED, danglingRegistry.lifecycle());
        assertThrows(IllegalStateException.class, danglingRegistry::freeze);
    }

    @Test
    void duplicateAndWrongKeyPreflightsLeaveRegistryRecoverable() {
        FabricDefaultedRegistry<TestValue> occupiedRegistry = registry();
        register(occupiedRegistry, DEFAULT_KEY, new TestValue(occupiedRegistry, "default"));
        AtomicBoolean occupiedSupplierCalled = new AtomicBoolean();
        FabricDeferredRegister<TestValue> occupiedBatch = new FabricDeferredRegister<>(REGISTRY_KEY, "registry_test");
        occupiedBatch.register("default", () -> {
            occupiedSupplierCalled.set(true);
            return new TestValue(occupiedRegistry, "duplicate");
        });
        assertThrows(IllegalStateException.class, () -> occupiedBatch.registerAll(occupiedRegistry));
        assertFalse(occupiedSupplierCalled.get());
        assertEquals(FabricRegistryLifecycle.FAILED, occupiedBatch.lifecycle());
        assertEquals(FabricCustomRegistryLifecycle.CREATED, occupiedRegistry.lifecycle());
        assertSame(occupiedRegistry, occupiedRegistry.freeze());

        FabricDefaultedRegistry<TestValue> identityRegistry = registry();
        TestValue defaultValue = new TestValue(identityRegistry, "default");
        register(identityRegistry, DEFAULT_KEY, defaultValue);
        assertThrows(IllegalStateException.class,
              () -> identityRegistry.register(OTHER_KEY, defaultValue, RegistrationInfo.BUILT_IN));
        assertThrows(NullPointerException.class,
              () -> identityRegistry.register(OTHER_KEY, new TestValue("null_info"), null));
        ResourceKey<TestValue> wrongKey = ResourceKey.create(OTHER_REGISTRY_KEY, id("wrong"));
        assertThrows(IllegalArgumentException.class,
              () -> identityRegistry.register(wrongKey, new TestValue("wrong"), RegistrationInfo.BUILT_IN));
        assertEquals(FabricCustomRegistryLifecycle.CREATED, identityRegistry.lifecycle());
        assertSame(identityRegistry, identityRegistry.freeze());
    }

    @Test
    void ownerThreadPreflightRejectsBeforeEffectsAndOwnerCanRecover() throws InterruptedException {
        FabricDefaultedRegistry<TestValue> registry = registry();
        AtomicInteger suppliers = new AtomicInteger();
        FabricDeferredRegister<TestValue> deferred = new FabricDeferredRegister<>(REGISTRY_KEY, "registry_test");
        deferred.register("default", () -> {
            suppliers.incrementAndGet();
            return new TestValue(registry, "default");
        });

        assertTrue(runOffOwner(() -> deferred.registerAll(registry)) instanceof IllegalStateException);
        assertEquals(0, suppliers.get());
        assertEquals(FabricRegistryLifecycle.COLLECTING, deferred.lifecycle());
        assertEquals(FabricCustomRegistryLifecycle.CREATED, registry.lifecycle());
        assertTrue(runOffOwner(registry::registerRoot) instanceof IllegalStateException);
        assertTrue(runOffOwner(() -> registry.createIntrusiveHolder(new TestValue("foreign"))) instanceof IllegalStateException);
        assertEquals(FabricCustomRegistryLifecycle.CREATED, registry.lifecycle());

        deferred.registerAll(registry);
        assertEquals(1, suppliers.get());
        assertEquals(FabricRegistryLifecycle.REGISTERED, deferred.lifecycle());
        assertSame(registry, registry.freeze());
    }

    @Test
    void partialDeferredCommitRetainsPrefixAndFreezeExposesLaterDanglingHolder() {
        FabricDefaultedRegistry<TestValue> registry = registry();
        FabricDeferredRegister<TestValue> deferred = new FabricDeferredRegister<>(REGISTRY_KEY, "registry_test");
        FabricDeferredHolder<TestValue> committed = deferred.register("default", () -> new TestValue(registry, "default"));
        AtomicReference<TestValue> dangling = new AtomicReference<>();
        deferred.register("dangling", () -> {
            dangling.set(new TestValue(registry, "dangling"));
            throw new IllegalStateException("supplier failed after creating its intrusive holder");
        });
        AtomicBoolean skipped = new AtomicBoolean();
        deferred.register("skipped", () -> {
            skipped.set(true);
            return new TestValue(registry, "skipped");
        });

        assertThrows(IllegalStateException.class, () -> deferred.registerAll(registry));

        assertEquals(FabricRegistryLifecycle.FAILED, deferred.lifecycle());
        assertSame(committed.get(), registry.get(DEFAULT_KEY));
        assertSame(committed.canonicalHolder(), registry.canonicalDefaultHolder());
        assertFalse(dangling.get().intrusiveHolder.isBound());
        assertFalse(skipped.get());
        assertEquals(FabricCustomRegistryLifecycle.CREATED, registry.lifecycle());
        assertThrows(IllegalStateException.class, registry::freeze);
        assertEquals(FabricCustomRegistryLifecycle.FAILED, registry.lifecycle());
    }

    private static FabricDefaultedRegistry<TestValue> registry() {
        return FabricDefaultedRegistry.create(REGISTRY_KEY, DEFAULT_KEY, false);
    }

    private static Holder.Reference<TestValue> register(FabricDefaultedRegistry<TestValue> registry,
          ResourceKey<TestValue> key, TestValue value) {
        return Registry.registerForHolder(registry, key, value);
    }

    private static Throwable runOffOwner(Runnable action) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        }, "fabric-custom-registry-non-owner");
        thread.start();
        thread.join();
        return failure.get();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("registry_test", path);
    }

    private static final class TestValue {
        private final String name;
        private final Holder.Reference<TestValue> intrusiveHolder;

        private TestValue(String name) {
            this.name = name;
            intrusiveHolder = null;
        }

        private TestValue(FabricDefaultedRegistry<TestValue> registry, String name) {
            this.name = name;
            intrusiveHolder = registry.createIntrusiveHolder(this);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
