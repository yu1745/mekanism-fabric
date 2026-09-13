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
 * <p>The two type parameters mirror the shape of a NeoForge deferred registry holder exactly: {@code R} is the registry element type that the {@link Holder} surface
 * is expressed over, and {@code T extends R} is the more specific declared value type. That split is what lets a field keep its precise declared value type while
 * remaining assignable to a widened {@code Holder<R>}, for example
 * {@code MekanismKeyedHolder<RecipeType<?>, RecipeType<CrusherRecipe>>} <em>is</em> a {@code Holder<RecipeType<?>>}. A single type parameter cannot express this,
 * because {@code Holder} is invariant in its element type.
 *
 * <p>Two keyed forms are supported:
 * <ul>
 *     <li>{@link #keyed(ResourceKey, Object)} and {@link #keyed(ResourceKey, ResourceLocation, Object)}: a value that is key-correct and identity stable but is
 *     <strong>not</strong> part of a registry. Used for the Mekanism recipe types before their registry write, and as the carrier that the canonical holder is
 *     attached back onto.</li>
 *     <li>{@link #registryKeyed(ResourceKey, Supplier)}: the value is resolved lazily by key out of an existing registry. Used for the toast item holders, which
 *     stay non-null and key-correct but legitimately unbound while the Mekanism item family is not admitted.</li>
 * </ul>
 *
 * <p>Nothing is ever written into a registry by this class. Whenever a vanilla {@link Holder} delegate is present, all {@link Holder} queries delegate to it; while
 * there is no delegate the key itself provides the answers. Creating a holder never touches a registry, and a registry-resolved holder only looks its key up on
 * first access.
 *
 * @param <R> Registry element type the {@link Holder} surface is expressed over.
 * @param <T> Declared value type, a subtype of {@code R}.
 */
@NothingNullByDefault
public class MekanismKeyedHolder<R, T extends R> implements Holder<R>, Supplier<T> {

    private final ResourceKey<R> key;
    @Nullable
    private final Supplier<? extends Registry<R>> registry;
    @Nullable
    private volatile Holder<R> delegate;
    @Nullable
    private T value;

    private MekanismKeyedHolder(ResourceKey<R> key, @Nullable Supplier<? extends Registry<R>> registry, @Nullable T value) {
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
    public static <R, T extends R> MekanismKeyedHolder<R, T> keyed(ResourceKey<R> key, T value) {
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
    public static <R, T extends R> MekanismKeyedHolder<R, T> keyed(ResourceKey<? extends Registry<R>> registryKey, ResourceLocation name, T value) {
        Objects.requireNonNull(name, "Holder name cannot be null.");
        return new MekanismKeyedHolder<>(ResourceKey.create(registryKey, name), null,
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
    @SuppressWarnings("unchecked")
    public static <R, T extends R> MekanismKeyedHolder<R, T> registryKeyed(ResourceKey<R> key, Supplier<? extends Registry<R>> registry) {
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
    public static <R, T extends R> MekanismKeyedHolder<R, T> registryKeyed(ResourceKey<? extends Registry<R>> registryKey, ResourceLocation name,
          Supplier<? extends Registry<R>> registry) {
        Objects.requireNonNull(name, "Holder name cannot be null.");
        return new MekanismKeyedHolder<>(ResourceKey.create(registryKey, name),
              Objects.requireNonNull(registry, "Registry supplier cannot be null."), null);
    }

    /**
     * Attaches the vanilla holder this keyed holder delegates to. Only allowed once and only for a matching key.
     *
     * <p>The attached holder must resolve to the <strong>same</strong> value instance this holder was built over, so that attaching cannot silently swap in an
     * unrelated instance and split identity. This is the mechanism that lets a prebuilt recipe type become registry bound without being recreated.
     *
     * @param delegate Holder to delegate to.
     */
    public void attach(Holder<R> delegate) {
        Objects.requireNonNull(delegate, "Delegate holder cannot be null.");
        ResourceKey<R> expected = delegate.unwrapKey().orElse(null);
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
            T current = value;
            if (current != null && delegate.isBound() && delegate.value() != current) {
                throw new IllegalArgumentException("Registry holder " + key.location()
                      + " resolves to a different value instance than this holder was built over");
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
    public ResourceKey<R> getKey() {
        return key;
    }

    /**
     * @return The resource key, matching {@link Holder#unwrapKey()}.
     */
    public ResourceKey<R> key() {
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
    @SuppressWarnings("unchecked")
    public T value() {
        Holder<R> holder = resolved();
        if (holder != null) {
            //Note: The registry only ever holds exactly the instance this holder was built over, which attach also verifies, so this narrowing is exact.
            return (T) holder.value();
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
        Holder<R> holder = resolved();
        if (holder != null) {
            return holder.isBound();
        }
        return value != null;
    }

    @Override
    public boolean is(ResourceLocation id) {
        Objects.requireNonNull(id, "Location cannot be null.");
        Holder<R> holder = resolved();
        return holder != null ? holder.is(id) : key.location().equals(id);
    }

    @Override
    public boolean is(ResourceKey<R> otherKey) {
        Objects.requireNonNull(otherKey, "Key cannot be null.");
        Holder<R> holder = resolved();
        return holder != null ? holder.is(otherKey) : key.equals(otherKey);
    }

    @Override
    public boolean is(Predicate<ResourceKey<R>> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null.");
        Holder<R> holder = resolved();
        return holder != null ? holder.is(predicate) : predicate.test(key);
    }

    @Override
    public boolean is(TagKey<R> tag) {
        Objects.requireNonNull(tag, "Tag cannot be null.");
        Holder<R> holder = resolved();
        return holder != null && holder.is(tag);
    }

    @Override
    @Deprecated
    public boolean is(Holder<R> holder) {
        Objects.requireNonNull(holder, "Holder cannot be null.");
        Holder<R> current = resolved();
        return current != null && current.is(holder);
    }

    @Override
    public Stream<TagKey<R>> tags() {
        Holder<R> holder = resolved();
        return holder != null ? holder.tags() : Stream.empty();
    }

    @Override
    public Either<ResourceKey<R>, R> unwrap() {
        Holder<R> holder = resolved();
        return holder != null ? holder.unwrap() : Either.left(key);
    }

    @Override
    public Optional<ResourceKey<R>> unwrapKey() {
        Holder<R> holder = resolved();
        return holder != null ? holder.unwrapKey() : Optional.of(key);
    }

    @Override
    public Kind kind() {
        Holder<R> holder = resolved();
        return holder != null ? holder.kind() : Kind.REFERENCE;
    }

    @Override
    public boolean canSerializeIn(HolderOwner<R> owner) {
        Objects.requireNonNull(owner, "Owner cannot be null.");
        Holder<R> holder = resolved();
        return holder != null && holder.canSerializeIn(owner);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof MekanismKeyedHolder<?, ?> holder && key.equals(holder.key);
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
    private Holder<R> resolved() {
        Holder<R> holder = delegate;
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
