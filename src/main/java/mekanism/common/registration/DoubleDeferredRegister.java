package mekanism.common.registration;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/** Ordered pair of actual native deferred registers (blocks before their block items). */
public class DoubleDeferredRegister<PRIMARY, SECONDARY> {

    private final MekanismDeferredRegister<PRIMARY> primaryRegister;
    private final MekanismDeferredRegister<SECONDARY> secondaryRegister;

    protected DoubleDeferredRegister(MekanismDeferredRegister<PRIMARY> primaryRegistry, MekanismDeferredRegister<SECONDARY> secondaryRegistry) {
        this.primaryRegister = primaryRegistry;
        this.secondaryRegister = secondaryRegistry;
    }

    protected DoubleDeferredRegister(String modid, ResourceKey<? extends Registry<PRIMARY>> primaryRegistryName,
          ResourceKey<? extends Registry<SECONDARY>> secondaryRegistryName) {
        this(modid, primaryRegistryName, new MekanismDeferredRegister<>(secondaryRegistryName, modid));
    }

    protected DoubleDeferredRegister(String modid, ResourceKey<? extends Registry<PRIMARY>> primaryRegistryName, MekanismDeferredRegister<SECONDARY> secondaryRegistry) {
        this(new MekanismDeferredRegister<>(primaryRegistryName, modid), secondaryRegistry);
    }

    public <P extends PRIMARY, S extends SECONDARY, W extends DoubleWrappedRegistryObject<PRIMARY, P, SECONDARY, S>> W register(String name,
          Supplier<? extends P> primarySupplier, Supplier<? extends S> secondarySupplier, BiFunction<MekanismDeferredHolder<PRIMARY, P>,
          MekanismDeferredHolder<SECONDARY, S>, W> objectWrapper) {
        return objectWrapper.apply(primaryRegister.register(name, primarySupplier), secondaryRegister.register(name, secondarySupplier));
    }

    public <P extends PRIMARY, S extends SECONDARY, W extends DoubleWrappedRegistryObject<PRIMARY, P, SECONDARY, S>> W register(String name,
          Supplier<? extends P> primarySupplier, Function<P, S> secondarySupplier, BiFunction<MekanismDeferredHolder<PRIMARY, P>,
          MekanismDeferredHolder<SECONDARY, S>, W> objectWrapper) {
        return registerAdvanced(name, primarySupplier, secondarySupplier.compose(Supplier::get), objectWrapper);
    }

    public <P extends PRIMARY, S extends SECONDARY, W extends DoubleWrappedRegistryObject<PRIMARY, P, SECONDARY, S>> W registerAdvanced(String name,
          Supplier<? extends P> primarySupplier, Function<MekanismDeferredHolder<PRIMARY, P>, S> secondarySupplier, BiFunction<MekanismDeferredHolder<PRIMARY, P>,
          MekanismDeferredHolder<SECONDARY, S>, W> objectWrapper) {
        MekanismDeferredHolder<PRIMARY, P> primaryObject = primaryRegister.register(name, primarySupplier);
        return objectWrapper.apply(primaryObject, secondaryRegister.register(name, () -> secondarySupplier.apply(primaryObject)));
    }

    /** The caller supplies real registry instances in native bootstrap order. No event bus or parallel register exists. */
    public void registerAll(Registry<PRIMARY> primary, Registry<SECONDARY> secondary) {
        primaryRegister.registerAll(primary);
        secondaryRegister.registerAll(secondary);
    }

    public Collection<MekanismDeferredHolder<PRIMARY, ? extends PRIMARY>> getPrimaryEntries() {
        return List.copyOf(primaryRegister.getEntries());
    }

    public Collection<MekanismDeferredHolder<SECONDARY, ? extends SECONDARY>> getSecondaryEntries() {
        return List.copyOf(secondaryRegister.getEntries());
    }
}
