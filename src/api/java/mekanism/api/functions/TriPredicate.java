package mekanism.api.functions;

import java.util.Objects;

/** Three-argument predicate with the same short-circuit composition contract as {@link java.util.function.Predicate}. */
@FunctionalInterface
public interface TriPredicate<A, B, C> {
    boolean test(A first, B second, C third);

    default TriPredicate<A, B, C> and(TriPredicate<? super A, ? super B, ? super C> other) {
        Objects.requireNonNull(other);
        return (a, b, c) -> test(a, b, c) && other.test(a, b, c);
    }

    default TriPredicate<A, B, C> negate() {
        return (a, b, c) -> !test(a, b, c);
    }

    default TriPredicate<A, B, C> or(TriPredicate<? super A, ? super B, ? super C> other) {
        Objects.requireNonNull(other);
        return (a, b, c) -> test(a, b, c) || other.test(a, b, c);
    }
}
