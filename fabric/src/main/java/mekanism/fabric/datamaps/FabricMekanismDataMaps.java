package mekanism.fabric.datamaps;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mekanism.api.datamaps.MekaSuitAbsorption;
import mekanism.api.datamaps.MekanismDataMapType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.damagesource.DamageType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-scoped lifecycle owner for the first ordinary Fabric data map. */
public final class FabricMekanismDataMaps {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricDataMaps");
    public static final MekanismDataMapType<DamageType, MekaSuitAbsorption> MEKASUIT_ABSORPTION =
          new MekanismDataMapType<>(Registries.DAMAGE_TYPE, MekaSuitAbsorption.ID, MekaSuitAbsorption.CODEC);
    private static final Map<MinecraftServer, Session> SESSIONS = new IdentityHashMap<>();
    private static final Set<MinecraftServer> FAILED_STARTS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean callbacksInitialized;

    private FabricMekanismDataMaps() {
    }

    /** Registers the three server lifecycle callbacks once for the process. */
    public static synchronized void initializeCallbacks() {
        if (callbacksInitialized) {
            return;
        }
        FabricDataMapLoader.validateCatalog(List.of(MEKASUIT_ABSORPTION));
        ServerLifecycleEvents.SERVER_STARTING.register(FabricMekanismDataMaps::start);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(FabricMekanismDataMaps::reloadFinished);
        ServerLifecycleEvents.SERVER_STOPPED.register(FabricMekanismDataMaps::stop);
        callbacksInitialized = true;
    }

    public static @Nullable MekaSuitAbsorption getMekaSuitAbsorption(MinecraftServer server, Holder<DamageType> damageType) {
        requireServerThread(server);
        Session session = requireSession(server);
        Registry<DamageType> registry = server.reloadableRegistries().get().registryOrThrow(Registries.DAMAGE_TYPE);
        return session.query(registry, damageType);
    }

    /** Resolve the actual server's base/world view or current reloadable view, on its owner thread only. */
    static synchronized @Nullable MekaSuitAbsorption getCurrent(net.minecraft.core.RegistryAccess access, Holder<DamageType> damageType) {
        for (Map.Entry<MinecraftServer, Session> entry : SESSIONS.entrySet()) {
            MinecraftServer server = entry.getKey();
            if (server.isSameThread() && (server.reloadableRegistries().get() == access || server.registryAccess() == access)) {
                //Entities expose the base/world view, not ReloadableServerRegistries.Holder#get(). They must
                //still resolve this exact server's damage registry, never an equivalent or foreign owner.
                Registry<DamageType> registry = server.reloadableRegistries().get().registryOrThrow(Registries.DAMAGE_TYPE);
                if (access.registryOrThrow(Registries.DAMAGE_TYPE) != registry) {
                    throw new IllegalStateException("Damage registry view does not belong to the current server");
                }
                return entry.getValue().query(registry, damageType);
            }
        }
        throw new IllegalStateException("No current server data-map context for registry access");
    }

    public static int epoch(MinecraftServer server) {
        requireServerThread(server);
        return requireSession(server).snapshot().epoch();
    }

    public static int size(MinecraftServer server) {
        requireServerThread(server);
        return requireSession(server).snapshot().values().size();
    }

    /** Intended for lifecycle diagnostics; identity lookup deliberately does not accept an equivalent server. */
    public static synchronized boolean hasSession(MinecraftServer server) {
        return SESSIONS.containsKey(server);
    }

    private static synchronized void start(MinecraftServer server) {
        requireServerThread(server);
        if (SESSIONS.containsKey(server) || FAILED_STARTS.contains(server)) {
            throw new IllegalStateException("Duplicate Fabric data-map server initialization");
        }
        Session session = new Session(Thread.currentThread());
        SESSIONS.put(server, session);
        try {
            session.load(server.getResourceManager(), server.reloadableRegistries().get(), "startup");
            FAILED_STARTS.remove(server);
        } catch (Throwable failure) {
            SESSIONS.remove(server);
            FAILED_STARTS.add(server);
            session.close();
            throw failure;
        }
    }

    private static void reloadFinished(MinecraftServer server, ResourceManager resources, boolean success) {
        if (!success) {
            LOGGER.warn("MEKANISM_FABRIC_DATAMAP_RELOAD_REJECTED success=false epoch_unchanged={}", safeEpoch(server));
            return;
        }
        try {
            requireServerThread(server);
            requireSession(server).load(resources, server.reloadableRegistries().get(), "reload");
        } catch (Throwable failure) {
            // The vanilla reload has already committed. This local owner retains only its prior local snapshot.
            LOGGER.error("Fabric data-map owner invariant failed after successful vanilla reload; retaining stale local snapshot (epoch={})",
                  safeEpoch(server), failure);
        }
    }

    private static synchronized void stop(MinecraftServer server) {
        requireServerThread(server);
        Session session = SESSIONS.remove(server);
        if (session == null) {
            if (FAILED_STARTS.remove(server)) {
                LOGGER.info("Fabric data-map failed-start cleanup completed for stopped server");
                return;
            }
            throw new IllegalStateException("Stopping Fabric data-map server without a session");
        }
        session.close();
        LOGGER.info("MEKANISM_FABRIC_DATAMAP_STOP_PROOF removed=true");
    }

    private static synchronized Session requireSession(MinecraftServer server) {
        Session session = SESSIONS.get(server);
        if (session == null) {
            throw new IllegalStateException("No active Fabric data-map session for server");
        }
        return session;
    }

    private static synchronized int safeEpoch(MinecraftServer server) {
        Session session = SESSIONS.get(server);
        return session == null || session.closed ? 0 : session.snapshot.epoch();
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Fabric data-map lifecycle/query must run on the server thread");
        }
    }

    static final class Session implements AutoCloseable {
        private final Thread ownerThread;
        private Snapshot snapshot = Snapshot.empty();
        private boolean loading;
        private boolean closed;

        Session(Thread ownerThread) {
            this.ownerThread = ownerThread;
        }

        void load(ResourceManager resources, HolderLookup.Provider registries, String phase) {
            checkThread();
            checkOpen();
            if (loading) {
                throw new IllegalStateException("Reentrant Fabric data-map load");
            }
            loading = true;
            try {
                FabricDataMapLoader.Result<DamageType, MekaSuitAbsorption> loaded =
                      FabricDataMapLoader.load(resources, registries, MEKASUIT_ABSORPTION);
                Registry<DamageType> registry = registry(registries);
                Snapshot candidate = new Snapshot(snapshot.epoch() + 1, loaded.values());
                verifyCandidate(registry, candidate);
                snapshot = candidate;
                LOGGER.info("MEKANISM_FABRIC_DATAMAP_PROOF phase={} epoch={} published={} skipped={}", phase,
                      candidate.epoch(), candidate.values().size(), loaded.skippedLayers());
            } finally {
                loading = false;
            }
        }

        @Nullable MekaSuitAbsorption query(Registry<DamageType> registry, Holder<DamageType> holder) {
            checkThread();
            checkOpen();
            ResourceKey<DamageType> key;
            if (holder.kind() == Holder.Kind.REFERENCE) {
                key = holder.unwrapKey().orElseThrow(() -> new IllegalArgumentException("Unbound reference damage-type holder"));
                if (!key.registry().equals(Registries.DAMAGE_TYPE.location())) {
                    throw new IllegalArgumentException("Damage-type holder belongs to the wrong registry: " + key.registry());
                }
            } else {
                key = registry.getResourceKey(holder.value())
                      .orElseThrow(() -> new IllegalArgumentException("Direct damage-type holder is not canonical in the current registry"));
            }
            return snapshot.values().get(key);
        }

        Snapshot snapshot() {
            checkThread();
            checkOpen();
            return snapshot;
        }

        @Override
        public void close() {
            checkThread();
            checkOpen();
            if (loading) {
                throw new IllegalStateException("Cannot close a loading Fabric data-map session");
            }
            closed = true;
            snapshot = Snapshot.empty();
        }

        private void verifyCandidate(Registry<DamageType> registry, Snapshot candidate) {
            if (candidate.epoch() <= 0) {
                throw new IllegalStateException("Fabric data-map epoch must be positive");
            }
            for (Map.Entry<ResourceKey<DamageType>, MekaSuitAbsorption> entry : candidate.values().entrySet()) {
                Holder.Reference<DamageType> holder = registry.getHolder(entry.getKey())
                      .orElseThrow(() -> new IllegalStateException("Published data-map key is absent from current registry: " + entry.getKey().location()));
                if (queryCandidate(registry, candidate, holder) != entry.getValue()) {
                    throw new IllegalStateException("Published data-map query invariant failed for " + entry.getKey().location());
                }
            }
        }

        private static @Nullable MekaSuitAbsorption queryCandidate(Registry<DamageType> registry, Snapshot candidate, Holder<DamageType> holder) {
            ResourceKey<DamageType> key = holder.unwrapKey().orElseGet(() -> registry.getResourceKey(holder.value()).orElse(null));
            return key == null ? null : candidate.values().get(key);
        }

        private static Registry<DamageType> registry(HolderLookup.Provider provider) {
            if (provider instanceof net.minecraft.core.RegistryAccess access) {
                return access.registryOrThrow(Registries.DAMAGE_TYPE);
            }
            throw new IllegalStateException("Fabric data-map owner requires RegistryAccess for canonical queries");
        }

        private void checkThread() {
            if (Thread.currentThread() != ownerThread) {
                throw new IllegalStateException("Fabric data-map session used off its server thread");
            }
        }

        private void checkOpen() {
            if (closed) {
                throw new IllegalStateException("Fabric data-map session is closed");
            }
        }
    }

    static record Snapshot(int epoch, Map<ResourceKey<DamageType>, MekaSuitAbsorption> values) {
        Snapshot {
            values = Map.copyOf(values);
        }

        static Snapshot empty() {
            return new Snapshot(0, Map.of());
        }
    }
}
