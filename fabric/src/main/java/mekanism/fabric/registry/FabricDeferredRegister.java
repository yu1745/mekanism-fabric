package mekanism.fabric.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
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
        Objects.requireNonNull(registrar);
        lifecycle = FabricRegistryLifecycle.REGISTERING;
        try {
            for (Map.Entry<ResourceKey<T>, Entry<T>> pending : entries.entrySet()) {
                T created = Objects.requireNonNull(pending.getValue().factory().get(),
                      "factory returned null for " + pending.getKey().location());
                T canonical = registrar.register(registryKey, pending.getKey(), created);
                pending.getValue().holder().bind(canonical);
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
