package mekanism.common.registration;

import com.mojang.datafixers.util.Either;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.fabric.registry.FabricDeferredHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

@NothingNullByDefault
public class MekanismDeferredHolder<R, T extends R> implements Holder<R>, Supplier<T>, INamedEntry {

    private final ResourceKey<R> key;
    @Nullable
    private volatile FabricDeferredHolder<R> delegate;

    public MekanismDeferredHolder(ResourceKey<? extends Registry<R>> registryKey, ResourceLocation valueName) {
        this(ResourceKey.create(registryKey, valueName));
    }

    public MekanismDeferredHolder(ResourceKey<R> key) {
        this.key = Objects.requireNonNull(key);
    }

    /** A wrapper shares its declared holder's native binding, never a second registry lookup or allocation. */
    protected MekanismDeferredHolder(MekanismDeferredHolder<R, ? extends T> source) {
        this(source.getKey());
        attach(source.requireDelegate());
    }

    void attach(FabricDeferredHolder<R> delegate) {
        if (this.delegate != null) {
            throw new IllegalStateException("Registry holder " + key.location() + " is already attached");
        }
        if (!key.equals(delegate.key())) {
            throw new IllegalArgumentException("Registry holder key mismatch: expected " + key.location() + " but got " + delegate.key().location());
        }
        this.delegate = delegate;
    }

    public ResourceKey<R> getKey() {
        return key;
    }

    public ResourceKey<R> key() {
        return key;
    }

    @Override
    public ResourceLocation getId() {
        return key.location();
    }

    @Override
    public String getName() {
        return INamedEntry.super.getName();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        return (T) requireDelegate().get();
    }

    @Override
    public T value() {
        return get();
    }

    public Optional<T> asOptional() {
        return isBound() ? Optional.of(get()) : Optional.empty();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof MekanismDeferredHolder<?, ?> holder && key.equals(holder.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "MekanismDeferredHolder{%s}", key);
    }

    @Override
    public boolean isBound() {
        FabricDeferredHolder<R> holder = delegate;
        return holder != null && holder.isBound();
    }

    @Override
    public boolean is(ResourceLocation id) {
        return key.location().equals(id);
    }

    @Override
    public boolean is(ResourceKey<R> otherKey) {
        return key.equals(otherKey);
    }

    @Override
    public boolean is(Predicate<ResourceKey<R>> predicate) {
        return predicate.test(key);
    }

    @Override
    public boolean is(TagKey<R> tag) {
        FabricDeferredHolder<R> holder = delegate;
        return holder != null && holder.is(tag);
    }

    @Override
    @Deprecated
    public boolean is(Holder<R> holder) {
        FabricDeferredHolder<R> platformHolder = delegate;
        return platformHolder != null && platformHolder.is(holder);
    }

    public boolean is(R other) {
        return get() == other;
    }

    public boolean keyMatches(Holder<R> holder) {
        return holder.is(key);
    }

    @Override
    public Stream<TagKey<R>> tags() {
        FabricDeferredHolder<R> holder = delegate;
        return holder == null ? Stream.empty() : holder.tags();
    }

    @Override
    public Either<ResourceKey<R>, R> unwrap() {
        return Either.left(key);
    }

    @Override
    public Optional<ResourceKey<R>> unwrapKey() {
        return Optional.of(key);
    }

    @Override
    public Kind kind() {
        return Kind.REFERENCE;
    }

    @Override
    public boolean canSerializeIn(HolderOwner<R> owner) {
        FabricDeferredHolder<R> holder = delegate;
        return holder != null && holder.canSerializeIn(owner);
    }

    private FabricDeferredHolder<R> requireDelegate() {
        FabricDeferredHolder<R> holder = delegate;
        if (holder == null) {
            throw new IllegalStateException("Registry holder " + key.location() + " is not attached to a deferred register");
        }
        return holder;
    }
}
