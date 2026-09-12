package mekanism.fabric.datamaps;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalDataContext;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.api.datamaps.MekanismDataMapType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;

/** One server/client chemical map epoch; candidates publish only after all five maps validate. */
public final class FabricChemicalDataMapSession implements AutoCloseable {
    private final Thread ownerThread;
    private ChemicalDataContext context = ChemicalDataContext.EMPTY;
    private boolean loading;
    private boolean closed;

    public FabricChemicalDataMapSession(Thread ownerThread) {
        this.ownerThread = java.util.Objects.requireNonNull(ownerThread);
    }

    public ChemicalDataContext load(ResourceManager resources, HolderLookup.Provider registries,
          List<MekanismDataMapType<Chemical, ?>> descriptors) {
        checkThread();
        checkOpen();
        if (loading) throw new IllegalStateException("Reentrant chemical data-map load");
        loading = true;
        try {
            RegistryLookup<Chemical> registry = registries.lookupOrThrow(ChemicalRegistry.REGISTRY_KEY);
            IdentityHashMap<MekanismDataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> candidate = new IdentityHashMap<>();
            for (MekanismDataMapType<Chemical, ?> descriptor : descriptors) {
                loadOne(resources, registries, descriptors, descriptor, candidate);
            }
            for (Map<ResourceKey<Chemical>, ?> values : candidate.values()) {
                for (ResourceKey<Chemical> key : values.keySet()) {
                    if (registry.get(key).isEmpty()) {
                        throw new IllegalStateException("Chemical data-map references an unknown chemical " + key.location());
                    }
                }
            }
            mekanism.common.config.ChemicalConfigAccess.ServerPolicy policy = mekanism.common.config.ChemicalConfigAccess.serverPolicy();
            ChemicalDataContext published = new ChemicalDataContext(context.epoch() + 1, candidate,
                  policy.radiationEnabled(), policy.feConversionRate(), policy.blacklistForge());
            context = published;
            ChemicalDataContext.bind(published);
            return published;
        } finally {
            loading = false;
        }
    }

    private static <T> void loadOne(ResourceManager resources, HolderLookup.Provider registries,
          List<MekanismDataMapType<Chemical, ?>> catalog, MekanismDataMapType<Chemical, T> descriptor,
          IdentityHashMap<MekanismDataMapType<Chemical, ?>, Map<ResourceKey<Chemical>, ?>> candidate) {
        FabricDataMapLoader.Result<Chemical, T> loaded = FabricDataMapLoader.load(resources, registries, descriptor, catalog);
        candidate.put(descriptor, loaded.values());
    }

    public ChemicalDataContext context() {
        checkThread();
        checkOpen();
        ChemicalDataContext.bind(context);
        return context;
    }

    public void publishSnapshot(ChemicalDataContext snapshot) {
        checkThread();
        checkOpen();
        if (snapshot.epoch() <= context.epoch()) throw new IllegalArgumentException("Chemical snapshot epoch must increase");
        context = snapshot;
        ChemicalDataContext.bind(snapshot);
    }

    @Override
    public void close() {
        checkThread();
        checkOpen();
        if (loading) throw new IllegalStateException("Cannot close a loading chemical data-map session");
        closed = true;
        context = ChemicalDataContext.EMPTY;
        ChemicalDataContext.clear();
    }

    private void checkThread() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Chemical data-map session used off owner thread");
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("Chemical data-map session is closed");
    }
}
