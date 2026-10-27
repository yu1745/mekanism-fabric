package mekanism.common.util;

import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric port replacement for NeoForge's {@code Lazy}: memoizes a non-null value across threads until invalidated.
 * A failed or null-producing supplier leaves the lazy unresolved so a later call may retry.
 */
public final class Lazy<T> implements Supplier<T> {

    private final Supplier<T> supplier;
    private T value;

    private Lazy(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public static <T> Lazy<T> of(@NotNull Supplier<T> supplier) {
        return new Lazy<>(supplier);
    }

    @Override
    public T get() {
        return getValue();
    }

    public synchronized T getValue() {
        if (value == null) {
            T computed = supplier.get();
            if (computed == null) {
                throw new IllegalStateException("Lazy value cannot be null, but supplier returned null: " + supplier);
            }
            value = computed;
        }
        return value;
    }

    public synchronized void invalidate() {
        value = null;
    }
}
