package mekanism.api.recipes;

import com.mojang.datafixers.util.Either;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

/**
 * Layer-neutral keyed holder. It is deliberately dependency free so it can live in {@code mekanism.api} while the platform keyed-holder implementation lives in the
 * common layer.
 *
 * <p>Two keyed forms are supported:
 * <ul>
 *     <li>{@link #keyed(ResourceKey, Object)} and {@link #keyed(ResourceKey, ResourceLocation, Object)}: a value that is key-correct and identity stable but is
 *     <strong>not</strong> part of a registry. Used for the Mekanism recipe types until recipe type registration is owned by a later task.</li>
 *     <li>{@link #registryKeyed(ResourceKey, Supplier)}: the value is resolved lazily by key out of an existing registry. Used for the toast item holders, which
 *     stay non-null and key-correct but legitimately unbound while the Mekanism item family is not admitted.</li>
 * </ul>
 *
 * <p>Nothing is ever written into a registry by this class. Whenever a vanilla {@link Holder} delegate is present, all {@link Holder} queries delegate to it; while
 * there is no delegate the key itself provides the answers. Creating a holder never touches a registry, and a registry-resolved holder only looks its key up on
 * first access.
 *
 * @param <T> Type of the held value.
 */
@NothingNullByDefault
public class MekanismKeyedHolder<T> implements Holder<T>, Supplier<T> {

    private final ResourceKey<T> key;
    @Nullable
    private final Supplier<? extends Registry<T>> registry;
    @Nullable
    private volatile Holder<T> delegate;
    @Nullable
    private T value;

    private MekanismKeyedHolder(ResourceKey<T> key, @Nullable Supplier<? extends Registry<T>> registry, @Nullable T value) {
        this.key = Objects.requireNonNull(key, "Holder key cannot be null.");
        this.registry = registry;
        this.value = value;
    }

    /**
     * Creates a keyed holder over an identity stable value that is not registered anywhere.
     *
     * @param key   Key this holder is keyed by.
     * @param value Value to expose. Must not be null.
     *
     * @return Keyed holder reporting exactly this key and value.
     */
    public static <T> MekanismKeyedHolder<T> keyed(ResourceKey<T> key, T value) {
        return new MekanismKeyedHolder<>(key, null, Objects.requireNonNull(value, "Held value cannot be null."));
    }

    /**
     * Creates a keyed holder over an identity stable value, deriving the key from its registry and name.
     *
     * <p>This mirrors the type relationship of a deferred registry holder: the registry is a {@code Registry<R>} while the value is a more specific {@code T}.
     *
     * @param registryKey Registry the key belongs to.
     * @param name        Name of the key within the registry.
     * @param value       Value to expose. Must not be null.
     *
     * @return Keyed holder reporting the derived key and this value.
     */
    @SuppressWarnings("unchecked")
    public static <R, T extends R> MekanismKeyedHolder<T> keyed(ResourceKey<? extends Registry<R>> registryKey, ResourceLocation name, T value) {
        Objects.requireNonNull(name, "Holder name cannot be null.");
        return new MekanismKeyedHolder<>((ResourceKey<T>) ResourceKey.create(registryKey, name), null,
              Objects.requireNonNull(value, "Held value cannot be null."));
    }

    /**
     * Creates a keyed holder that resolves its value by key out of the given registry on first access.
     *
     * @param key      Key to resolve.
     * @param registry Supplier of the registry to resolve against, evaluated lazily so that creating the holder never touches a registry.
     *
     * @return Keyed holder that stays unbound while the key is absent from the registry.
     */
    public static <T> MekanismKeyedHolder<T> registryKeyed(ResourceKey<T> key, Supplier<? extends Registry<T>> registry) {
        return new MekanismKeyedHolder<>(key, Objects.requireNonNull(registry, "Registry supplier cannot be null."), null);
    }

    /**
     * Creates a keyed holder that resolves its value by key out of the given registry on first access, deriving the key from the registry and name.
     *
     * <p>This mirrors the type relationship of a deferred registry holder: the registry is a {@code Registry<R>} while the value is a more specific {@code T}.
     *
     * @param registryKey Registry the key belongs to.
     * @param name        Name of the key within the registry.
     * @param registry    Supplier of the registry to resolve against, evaluated lazily so that creating the holder never touches a registry.
     *
     * @return Keyed holder that stays unbound while the key is absent from the registry.
     */
    @SuppressWarnings("unchecked")
    public static <R, T extends R> MekanismKeyedHolder<T> registryKeyed(ResourceKey<? extends Registry<R>> registryKey, ResourceLocation name,
          Supplier<? extends Registry<R>> registry) {
        Objects.requireNonNull(name, "Holder name cannot be null.");
        return new MekanismKeyedHolder<>((ResourceKey<T>) ResourceKey.create(registryKey, name),
              (Supplier<? extends Registry<T>>) (Supplier<?>) Objects.requireNonNull(registry, "Registry supplier cannot be null."), null);
    }

    /**
     * Attaches the vanilla holder this keyed holder delegates to. Only allowed once and only for a matching key.
     *
     * @param delegate Holder to delegate to.
     */
    public void attach(Holder<T> delegate) {
        Objects.requireNonNull(delegate, "Delegate holder cannot be null.");
        ResourceKey<T> expected = delegate.unwrapKey().orElse(null);
        if (expected != null && !key.equals(expected)) {
            throw new IllegalArgumentException("Registry holder key mismatch: expected " + key.location() + " but got " + expected.location());
        }
        synchronized (this) {
            if (this.delegate != null) {
                throw new IllegalStateException("Registry holder " + key.location() + " is already attached");
            }
            if (registry != null && value == null) {
                throw new IllegalStateException("Registry holder " + key.location() + " resolves itself by key and cannot be attached manually");
            }
            this.delegate = delegate;
            this.value = null;
        }
    }

    /**
     * Gets the resource key this holder is keyed by. Unlike {@link #unwrapKey()} it is always available, even while the holder is unbound.
     *
     * @return Resource key of this holder.
     */
    public ResourceKey<T> getKey() {
        return key;
    }

    /**
     * @return The resource key, matching {@link Holder#unwrapKey()}.
     */
    public ResourceKey<T> key() {
        return key;
    }

    /**
     * @return Path of this holder's key, mirroring the common-layer named-entry contract without depending on the common layer.
     */
    public String getName() {
        return key.location().getPath();
    }

    /**
     * @return Location of this holder's key, mirroring the common-layer named-entry contract without depending on the common layer.
     */
    public ResourceLocation getId() {
        return key.location();
    }

    @Override
    public T get() {
        return value();
    }

    @Override
    public T value() {
        Holder<T> holder = resolved();
        if (holder != null) {
            return holder.value();
        }
        T current = value;
        if (current != null) {
            return current;
        }
        throw new IllegalStateException("Mekanism keyed holder " + key.location() + " is not bound");
    }

    /**
     * @return The value if this holder is currently bound, otherwise {@link Optional#empty()}.
     */
    public Optional<T> asOptional() {
        return isBound() ? Optional.of(value()) : Optional.empty();
    }

    @Override
    public boolean isBound() {
        Holder<T> holder = resolved();
        if (holder != null) {
            return holder.isBound();
        }
        return value != null;
    }

    @Override
    public boolean is(ResourceLocation id) {
        Objects.requireNonNull(id, "Location cannot be null.");
        Holder<T> holder = resolved();
        return holder != null ? holder.is(id) : key.location().equals(id);
    }

    @Override
    public boolean is(ResourceKey<T> otherKey) {
        Objects.requireNonNull(otherKey, "Key cannot be null.");
        Holder<T> holder = resolved();
        return holder != null ? holder.is(otherKey) : key.equals(otherKey);
    }

    @Override
    public boolean is(Predicate<ResourceKey<T>> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null.");
        Holder<T> holder = resolved();
        return holder != null ? holder.is(predicate) : predicate.test(key);
    }

    @Override
    public boolean is(TagKey<T> tag) {
        Objects.requireNonNull(tag, "Tag cannot be null.");
        Holder<T> holder = resolved();
        return holder != null && holder.is(tag);
    }

    @Override
    @Deprecated
    public boolean is(Holder<T> holder) {
        Objects.requireNonNull(holder, "Holder cannot be null.");
        Holder<T> current = resolved();
        return current != null && current.is(holder);
    }

    @Override
    public Stream<TagKey<T>> tags() {
        Holder<T> holder = resolved();
        return holder != null ? holder.tags() : Stream.empty();
    }

    @Override
    public Either<ResourceKey<T>, T> unwrap() {
        Holder<T> holder = resolved();
        return holder != null ? holder.unwrap() : Either.left(key);
    }

    @Override
    public Optional<ResourceKey<T>> unwrapKey() {
        Holder<T> holder = resolved();
        return holder != null ? holder.unwrapKey() : Optional.of(key);
    }

    @Override
    public Kind kind() {
        Holder<T> holder = resolved();
        return holder != null ? holder.kind() : Kind.REFERENCE;
    }

    @Override
    public boolean canSerializeIn(HolderOwner<T> owner) {
        Objects.requireNonNull(owner, "Owner cannot be null.");
        Holder<T> holder = resolved();
        return holder != null && holder.canSerializeIn(owner);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof MekanismKeyedHolder<?> holder && key.equals(holder.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "MekanismKeyedHolder{%s}", key);
    }

    /**
     * Resolves the registry delegate on first access. Returns {@code null} while the key is absent from the registry and no delegate was attached.
     */
    @Nullable
    private Holder<T> resolved() {
        Holder<T> holder = delegate;
        if (holder != null || registry == null) {
            return holder;
        }
        synchronized (this) {
            holder = delegate;
            if (holder == null) {
                holder = registry.get().getHolder(key).orElse(null);
                if (holder == null) {
                    return null;
                }
                delegate = holder;
            }
            return holder;
        }
    }
}
