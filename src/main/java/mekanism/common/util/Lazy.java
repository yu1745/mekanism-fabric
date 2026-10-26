package mekanism.common.util;

import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric port delta: project-owned replacement for the NeoForge {@code Lazy} utility. Single-thread lazy memoization with the same observable contract:
 * the supplier runs at most once, on first {@link #getValue()}, and the same instance is returned afterwards. Not thread-safe, matching upstream.
 */
public final class Lazy<T> implements Supplier<T> {

    private final Supplier<T> supplier;
    private boolean resolved;
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

    public T getValue() {
        if (!resolved) {
            value = supplier.get();
            resolved = true;
        }
        return value;
    }
}
