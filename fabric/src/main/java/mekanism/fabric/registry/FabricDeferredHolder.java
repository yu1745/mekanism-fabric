package mekanism.fabric.registry;

import com.mojang.datafixers.util.Either;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/** A stable keyed holder which is unavailable until its deferred registration has completed. */
public final class FabricDeferredHolder<T> implements Holder<T>, Supplier<T> {
    private final ResourceKey<T> key;
    private volatile T value;
    private volatile Holder.Reference<T> canonicalHolder;

    FabricDeferredHolder(ResourceKey<T> key) {
        this.key = Objects.requireNonNull(key);
    }

    public ResourceKey<T> key() {
        return key;
    }

    @Override
    public T get() {
        return value();
    }

    @Override
    public T value() {
        T bound = value;
        if (bound == null) {
            throw new IllegalStateException("Registry value " + key.location() + " is not bound");
        }
        return bound;
    }

    @Override
    public boolean isBound() {
        return value != null;
    }

    @Override
    public boolean is(ResourceLocation id) {
        return key.location().equals(id);
    }

    @Override
    public boolean is(ResourceKey<T> otherKey) {
        return key.equals(otherKey);
    }

    @Override
    public boolean is(Predicate<ResourceKey<T>> predicate) {
        return predicate.test(key);
    }

    @Override
    public boolean is(TagKey<T> tag) {
        Holder<T> holder = canonicalHolder;
        return holder != null && holder.is(tag);
    }

    @Override
    @Deprecated
    public boolean is(Holder<T> holder) {
        Holder<T> canonical = canonicalHolder;
        return canonical != null && canonical.is(holder);
    }

    @Override
    public Stream<TagKey<T>> tags() {
        Holder<T> holder = canonicalHolder;
        return holder == null ? Stream.empty() : holder.tags();
    }

    @Override
    public Either<ResourceKey<T>, T> unwrap() {
        return Either.left(key);
    }

    @Override
    public Optional<ResourceKey<T>> unwrapKey() {
        return Optional.of(key);
    }

    @Override
    public Kind kind() {
        return Kind.REFERENCE;
    }

    @Override
    public boolean canSerializeIn(HolderOwner<T> owner) {
        Holder.Reference<T> holder = canonicalHolder;
        return holder != null && holder.canSerializeIn(owner);
    }

    /** Returns the exact canonical reference returned by vanilla registration. */
    public Holder.Reference<T> canonicalHolder() {
        Holder.Reference<T> holder = canonicalHolder;
        if (holder == null) {
            throw new IllegalStateException("Registry holder " + key.location() + " has no canonical reference");
        }
        return holder;
    }

    synchronized void bind(T canonicalValue) {
        bind(canonicalValue, null);
    }

    synchronized void bind(T canonicalValue, Holder.Reference<T> registryHolder) {
        if (value != null) {
            throw new IllegalStateException("Registry value " + key.location() + " is already bound");
        }
        T nonNullValue = Objects.requireNonNull(canonicalValue, "registrar returned null for " + key.location());
        if (registryHolder != null && (registryHolder.key() != key || registryHolder.value() != nonNullValue)) {
            throw new IllegalArgumentException("Registry holder does not match canonical value " + key.location());
        }
        canonicalHolder = registryHolder;
        value = nonNullValue;
    }
}
