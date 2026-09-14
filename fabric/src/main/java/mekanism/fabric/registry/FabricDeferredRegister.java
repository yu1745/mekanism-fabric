package mekanism.fabric.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Minimal synchronous deferred registration used only by the Mekanism Fabric port.
 * It intentionally does not emulate a NeoForge event bus or registry lifecycle.
 */
public final class FabricDeferredRegister<T> {
    @FunctionalInterface
    public interface Registrar<T> {
        T register(ResourceKey<? extends Registry<T>> registryKey, ResourceKey<T> key, T value);
    }

    private record Entry<T>(Supplier<? extends T> factory, FabricDeferredHolder<T> holder) {
    }

    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String namespace;
    private final Map<ResourceKey<T>, Entry<T>> entries = new LinkedHashMap<>();
    private FabricRegistryLifecycle lifecycle = FabricRegistryLifecycle.COLLECTING;

    public FabricDeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this.registryKey = Objects.requireNonNull(registryKey);
        this.namespace = Objects.requireNonNull(namespace);
    }

    public synchronized FabricDeferredHolder<T> register(String path, Supplier<? extends T> factory) {
        requireLifecycle(FabricRegistryLifecycle.COLLECTING, "collect entries");
        ResourceKey<T> key = ResourceKey.create(registryKey, ResourceLocation.fromNamespaceAndPath(namespace, path));
        FabricDeferredHolder<T> holder = new FabricDeferredHolder<>(key);
        if (entries.putIfAbsent(key, new Entry<>(Objects.requireNonNull(factory), holder)) != null) {
            throw new IllegalArgumentException("Duplicate registry key " + key.location());
        }
        return holder;
    }

    public synchronized void registerAll(Registrar<T> registrar) {
        requireLifecycle(FabricRegistryLifecycle.COLLECTING, "register entries");
        registerAll(Objects.requireNonNull(registrar), null);
    }

    /** Registers through a vanilla registry and retains its exact canonical holder reference. */
    public synchronized void registerAll(Registry<T> registry) {
        Objects.requireNonNull(registry);
        if (!registryKey.equals(registry.key())) {
            throw new IllegalArgumentException("Expected registry " + registryKey.location() + " but got " + registry.key().location());
        }
        requireLifecycle(FabricRegistryLifecycle.COLLECTING, "register entries");
        if (registry instanceof FabricDefaultedRegistry<?> customRegistry) {
            customRegistry.preflightDeferredRegistration();
        }
        registerAll(null, registry);
    }

    private void registerAll(Registrar<T> registrar, Registry<T> registry) {
        lifecycle = FabricRegistryLifecycle.REGISTERING;
        try {
            for (Map.Entry<ResourceKey<T>, Entry<T>> pending : entries.entrySet()) {
                ResourceKey<T> key = pending.getKey();
                if (registry != null && (registry.containsKey(key) || registry.containsKey(key.location()))) {
                    throw new IllegalStateException("Registry key is already occupied: " + key.location());
                }
                T created = Objects.requireNonNull(pending.getValue().factory().get(),
                      "factory returned null for " + key.location());
                if (registry == null) {
                    pending.getValue().holder().bind(registrar.register(registryKey, key, created));
                } else {
                    if (registry.getResourceKey(created).isPresent()) {
                        throw new IllegalStateException("Registry value is already registered by identity: " + created);
                    }
                    Holder.Reference<T> canonical = Registry.registerForHolder(registry, key, created);
                    // Vanilla non-intrusive MappedRegistry binds reference values at freeze, not register. Validate through
                    // its authoritative value/holder maps now; retain that same reference for later tags and serialization.
                    if (registry.getResourceKey(created).orElse(null) != key || registry.getHolder(key).orElse(null) != canonical
                          || registry.getId(created) < 0) {
                        throw new IllegalStateException("Registry did not retain canonical value/holder for " + key.location());
                    }
                    pending.getValue().holder().bind(created, canonical);
                }
            }
            lifecycle = FabricRegistryLifecycle.REGISTERED;
        } catch (RuntimeException | Error failure) {
            lifecycle = FabricRegistryLifecycle.FAILED;
            throw failure;
        }
    }

    public synchronized FabricRegistryLifecycle lifecycle() {
        return lifecycle;
    }

    private void requireLifecycle(FabricRegistryLifecycle expected, String operation) {
        if (lifecycle != expected) {
            throw new IllegalStateException("Cannot " + operation + " while registry lifecycle is " + lifecycle);
        }
    }
}
