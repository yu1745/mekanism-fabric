package mekanism.common.registration;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.fabric.registry.FabricDeferredHolder;
import mekanism.fabric.registry.FabricDeferredRegister;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

@NothingNullByDefault
public class MekanismDeferredRegister<T> {

    @FunctionalInterface
    interface Registrar<T> {
        T register(ResourceKey<? extends Registry<T>> registryKey, ResourceKey<T> key, T value);
    }

    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String namespace;
    private final Function<ResourceKey<T>, ? extends MekanismDeferredHolder<T, ?>> holderCreator;
    private final FabricDeferredRegister<T> delegate;
    private final List<MekanismDeferredHolder<T, ? extends T>> entries = new ArrayList<>();
    private final List<MekanismDeferredHolder<T, ? extends T>> entriesView = Collections.unmodifiableList(entries);

    public MekanismDeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this(registryKey, namespace, MekanismDeferredHolder::new);
    }

    public MekanismDeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace,
          Function<ResourceKey<T>, ? extends MekanismDeferredHolder<T, ? extends T>> holderCreator) {
        this.registryKey = Objects.requireNonNull(registryKey);
        this.namespace = Objects.requireNonNull(namespace);
        this.holderCreator = Objects.requireNonNull(holderCreator);
        delegate = new FabricDeferredRegister<>(registryKey, namespace);
    }

    public <I extends T> MekanismDeferredHolder<T, I> register(String name, Function<ResourceLocation, ? extends I> factory) {
        Objects.requireNonNull(factory);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, name);
        return register(name, () -> factory.apply(id));
    }

    @SuppressWarnings("unchecked")
    public <I extends T> MekanismDeferredHolder<T, I> register(String name, Supplier<? extends I> supplier) {
        Objects.requireNonNull(supplier);
        ResourceKey<T> key = ResourceKey.create(registryKey, ResourceLocation.fromNamespaceAndPath(namespace, name));
        MekanismDeferredHolder<T, I> holder = (MekanismDeferredHolder<T, I>) holderCreator.apply(key);
        if (!key.equals(holder.getKey())) {
            throw new IllegalArgumentException("Holder creator returned the wrong key for " + key.location());
        }
        FabricDeferredHolder<T> fabricHolder = delegate.register(name, supplier);
        holder.attach(fabricHolder);
        entries.add(holder);
        return holder;
    }

    /**
     * Declares one canonical holder before its deferred value factory is evaluated. Menu factories that pass their own
     * registered type into constructed menus must capture this holder, not manufacture an unattached alias.
     */
    @SuppressWarnings("unchecked")
    protected <I extends T, H extends MekanismDeferredHolder<T, I>> H registerWithHolder(String name, Function<H, ? extends I> factory) {
        Objects.requireNonNull(factory);
        ResourceKey<T> key = ResourceKey.create(registryKey, ResourceLocation.fromNamespaceAndPath(namespace, name));
        H holder = (H) holderCreator.apply(key);
        if (!key.equals(holder.getKey())) {
            throw new IllegalArgumentException("Holder creator returned the wrong key for " + key.location());
        }
        FabricDeferredHolder<T> fabricHolder = delegate.register(name, () -> factory.apply(holder));
        holder.attach(fabricHolder);
        entries.add(holder);
        return holder;
    }

    /** Canonical declared holders in registration order; never constructs a second holder or evaluates suppliers. */
    public List<? extends MekanismDeferredHolder<T, ? extends T>> getEntries() {
        return entriesView;
    }

    public String getNamespace() {
        return namespace;
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return registryKey;
    }

    /** Commits this batch through the normal vanilla registry lifecycle. */
    public void registerAll(Registry<T> registry) {
        delegate.registerAll(registry);
    }

    /** Package-private test seam for canonical binding semantics without mutating a global registry. */
    void registerAll(Registrar<T> registrar) {
        Objects.requireNonNull(registrar);
        delegate.registerAll(registrar::register);
    }
}
