package mekanism.fabric.registry;

import com.mojang.serialization.Lifecycle;
import java.util.Objects;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

/**
 * Intrusive, defaulted custom registry with a fail-closed lifecycle.
 *
 * <p>All population, root registration, and the mutable-to-frozen transition belong to the thread that creates the registry.
 * Already-frozen calls are idempotent on reload workers. Synchronization serializes mutations; it does not make population reads safe.
 */
public final class FabricDefaultedRegistry<T> extends DefaultedMappedRegistry<T> {
    private final ResourceKey<T> defaultEntryKey;
    private final Thread ownerThread;
    private final boolean synced;
    private FabricCustomRegistryLifecycle lifecycle = FabricCustomRegistryLifecycle.CREATED;
    private Holder.Reference<T> canonicalDefaultHolder;

    public static <T> FabricDefaultedRegistry<T> create(ResourceKey<? extends Registry<T>> registryKey,
          ResourceKey<T> defaultEntryKey, boolean synced) {
        return new FabricDefaultedRegistry<>(registryKey, defaultEntryKey, synced);
    }

    private FabricDefaultedRegistry(ResourceKey<? extends Registry<T>> registryKey, ResourceKey<T> defaultEntryKey,
          boolean synced) {
        super(validateDefaultEntryKey(registryKey, defaultEntryKey).location().toString(), registryKey,
              Lifecycle.stable(), true);
        this.defaultEntryKey = defaultEntryKey;
        this.ownerThread = Thread.currentThread();
        this.synced = synced;
    }

    private static <T> ResourceKey<T> validateDefaultEntryKey(ResourceKey<? extends Registry<T>> registryKey,
          ResourceKey<T> defaultEntryKey) {
        Objects.requireNonNull(registryKey, "registry key");
        Objects.requireNonNull(defaultEntryKey, "default entry key");
        if (!defaultEntryKey.isFor(registryKey)) {
            throw new IllegalArgumentException("Default entry " + defaultEntryKey + " is not for registry " + registryKey.location());
        }
        return defaultEntryKey;
    }

    @Override
    public synchronized Holder.Reference<T> register(ResourceKey<T> entryKey, T value, RegistrationInfo registrationInfo) {
        requireOwnerThread("register entries");
        requireMutableLifecycle("register entries");
        Objects.requireNonNull(entryKey, "entry key");
        Objects.requireNonNull(value, "registry value");
        Objects.requireNonNull(registrationInfo, "registration info");
        if (!entryKey.isFor(key())) {
            throw new IllegalArgumentException("Entry " + entryKey + " is not for registry " + key().location());
        }
        if (containsKey(entryKey) || containsKey(entryKey.location())) {
            throw new IllegalStateException("Registry key is already occupied: " + entryKey.location());
        }
        if (getResourceKey(value).isPresent()) {
            throw new IllegalStateException("Registry value is already registered by identity: " + value);
        }
        try {
            Holder.Reference<T> registered = super.register(entryKey, value, registrationInfo);
            if (entryKey == defaultEntryKey) {
                verifyCanonicalReference(registered, entryKey, value);
                canonicalDefaultHolder = registered;
            }
            return registered;
        } catch (RuntimeException | Error failure) {
            lifecycle = FabricCustomRegistryLifecycle.FAILED;
            throw failure;
        }
    }

    @Override
    public synchronized Holder.Reference<T> createIntrusiveHolder(T value) {
        requireOwnerThread("create intrusive holders");
        requireMutableLifecycle("create intrusive holders");
        Objects.requireNonNull(value, "registry value");
        if (getResourceKey(value).isPresent()) {
            throw new IllegalStateException("Registry value is already registered by identity: " + value);
        }
        try {
            Holder.Reference<T> holder = super.createIntrusiveHolder(value);
            if (holder.value() != value || !holder.canSerializeIn(holderOwner())) {
                throw new IllegalStateException("Intrusive holder does not belong to registry " + key().location());
            }
            return holder;
        } catch (RuntimeException | Error failure) {
            lifecycle = FabricCustomRegistryLifecycle.FAILED;
            throw failure;
        }
    }

    /** Registers this exact instance in the vanilla root registry once. */
    public synchronized FabricDefaultedRegistry<T> registerRoot() {
        requireOwnerThread("register the root registry");
        requireLifecycle(FabricCustomRegistryLifecycle.CREATED, "register the root registry");
        Registry<Registry<?>> root = rootRegistry();
        if (!key().isFor(root.key())) {
            throw new IllegalArgumentException("Registry key " + key() + " is not an entry of the root registry");
        }
        if (root.containsKey(key().location()) || root.getResourceKey(this).isPresent()) {
            throw new IllegalStateException("Root registry already contains " + key().location());
        }

        lifecycle = FabricCustomRegistryLifecycle.REGISTERING_ROOT;
        try {
            FabricRegistryBuilder<T, FabricDefaultedRegistry<T>> builder = FabricRegistryBuilder.from(this);
            if (synced) {
                builder.attribute(RegistryAttribute.SYNCED);
            }
            FabricDefaultedRegistry<T> registered = builder.buildAndRegister();
            ResourceKey<?> rootedKey = root.getResourceKey(this).orElse(null);
            if (registered != this || root.get(key().location()) != this || rootedKey != key()) {
                throw new IllegalStateException("Root registry did not retain the exact custom registry " + key().location());
            }
            RegistryAttributeHolder attributes = RegistryAttributeHolder.get(this);
            if (!attributes.hasAttribute(RegistryAttribute.MODDED)
                  || attributes.hasAttribute(RegistryAttribute.SYNCED) != synced) {
                throw new IllegalStateException("Root registry attributes do not match " + key().location());
            }
            lifecycle = FabricCustomRegistryLifecycle.ROOT_REGISTERED;
            return this;
        } catch (RuntimeException | Error failure) {
            lifecycle = FabricCustomRegistryLifecycle.FAILED;
            throw failure;
        }
    }

    /** Returns the canonical vanilla holder produced by registering the configured default entry. */
    public synchronized Holder.Reference<T> canonicalDefaultHolder() {
        if (canonicalDefaultHolder == null) {
            throw new IllegalStateException("Default entry is not registered: " + defaultEntryKey.location());
        }
        return canonicalDefaultHolder;
    }

    @Override
    public synchronized FabricDefaultedRegistry<T> freeze() {
        // RegistryAccess snapshots may idempotently re-freeze custom roots from a reload worker.
        // The actual mutable-to-frozen transition remains construction-thread-only.
        if (lifecycle == FabricCustomRegistryLifecycle.FROZEN) {
            return this;
        }
        requireOwnerThread("freeze the registry");
        if (lifecycle != FabricCustomRegistryLifecycle.CREATED
              && lifecycle != FabricCustomRegistryLifecycle.ROOT_REGISTERED) {
            throw new IllegalStateException("Cannot freeze registry while lifecycle is " + lifecycle);
        }
        Holder.Reference<T> defaultHolder = canonicalDefaultHolder;
        if (defaultHolder == null) {
            throw new IllegalStateException("Cannot freeze registry without default entry " + defaultEntryKey.location());
        }
        verifyCanonicalReference(defaultHolder, defaultEntryKey, defaultHolder.value());

        lifecycle = FabricCustomRegistryLifecycle.FREEZING;
        try {
            Registry<T> frozen = super.freeze();
            if (frozen != this) {
                throw new IllegalStateException("Vanilla freeze returned a different registry for " + key().location());
            }
            lifecycle = FabricCustomRegistryLifecycle.FROZEN;
            return this;
        } catch (RuntimeException | Error failure) {
            lifecycle = FabricCustomRegistryLifecycle.FAILED;
            throw failure;
        }
    }

    public synchronized FabricCustomRegistryLifecycle lifecycle() {
        return lifecycle;
    }

    synchronized void preflightDeferredRegistration() {
        requireOwnerThread("register deferred entries");
        requireMutableLifecycle("register deferred entries");
    }

    private void verifyCanonicalReference(Holder.Reference<T> holder, ResourceKey<T> entryKey, T value) {
        if (holder == null || holder.key() != entryKey || holder.value() != value || !holder.canSerializeIn(holderOwner())
              || getHolder(entryKey).orElse(null) != holder || getResourceKey(value).orElse(null) != entryKey
              || getOptional(entryKey.location()).orElse(null) != value) {
            throw new IllegalStateException("Registry did not retain the exact canonical holder for " + entryKey.location());
        }
    }

    private void requireMutableLifecycle(String operation) {
        if (lifecycle != FabricCustomRegistryLifecycle.CREATED
              && lifecycle != FabricCustomRegistryLifecycle.ROOT_REGISTERED) {
            throw new IllegalStateException("Cannot " + operation + " while registry lifecycle is " + lifecycle);
        }
    }

    private void requireLifecycle(FabricCustomRegistryLifecycle expected, String operation) {
        if (lifecycle != expected) {
            throw new IllegalStateException("Cannot " + operation + " while registry lifecycle is " + lifecycle);
        }
    }

    private void requireOwnerThread(String operation) {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Only the construction thread may " + operation + " for " + key().location());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Registry<Registry<?>> rootRegistry() {
        return (Registry) BuiltInRegistries.REGISTRY;
    }
}
