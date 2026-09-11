package mekanism.fabric.registry;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;

/** A holder which is unavailable until its deferred registration has completed. */
public final class FabricDeferredHolder<T> {
    private final ResourceKey<T> key;
    private volatile T value;

    FabricDeferredHolder(ResourceKey<T> key) {
        this.key = Objects.requireNonNull(key);
    }

    public ResourceKey<T> key() {
        return key;
    }

    public T get() {
        T bound = value;
        if (bound == null) {
            throw new IllegalStateException("Registry value " + key.location() + " is not bound");
        }
        return bound;
    }

    void bind(T canonicalValue) {
        if (value != null) {
            throw new IllegalStateException("Registry value " + key.location() + " is already bound");
        }
        value = Objects.requireNonNull(canonicalValue, "registrar returned null for " + key.location());
    }
}
