package mekanism.fabric.chemical;

import java.util.Objects;
import mekanism.api.chemical.ChemicalDataContext;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Physical-client owner for name-based chemical snapshots and reconnect cleanup. */
public final class FabricChemicalClientLifecycle {
    private static String sessionId;
    private static long epoch;
    private static long generation;
    private static boolean connectionOpen;
    private static boolean initialized;
    private static boolean stopped;

    private FabricChemicalClientLifecycle() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> {
            long disconnectedGeneration = generation();
            client.execute(() -> disconnectCurrent(disconnectedGeneration));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            long disconnectedGeneration = generation();
            client.execute(() -> disconnectCurrent(disconnectedGeneration));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stop());
        initialized = true;
    }

    /** Marks decoded configuration work before its client-executor handoff, so disconnect can invalidate it. */
    public static synchronized long beginConfiguration() {
        if (stopped) throw new IllegalStateException("Chemical client is stopped");
        connectionOpen = true;
        return generation;
    }

    public static synchronized long generation() { return generation; }

    public static synchronized boolean isCurrentGeneration(long candidate) {
        return !stopped && candidate == generation;
    }

    /** Runs only after a network handler has handed the decoded snapshot to the client executor. */
    public static synchronized void publish(String incomingSession, ChemicalDataContext context, boolean initial, long queuedGeneration) {
        Objects.requireNonNull(incomingSession);
        Objects.requireNonNull(context);
        if (stopped || queuedGeneration != generation) throw new IllegalStateException("Late chemical snapshot after disconnect/stop");
        if (incomingSession.isBlank() || incomingSession.length() > 64 || context.epoch() <= 0) throw new IllegalArgumentException("Invalid chemical session/epoch");
        if (initial) {
            if (sessionId == null) {
                sessionId = incomingSession;
                connectionOpen = true;
            } else if (!sessionId.equals(incomingSession)) {
                throw new IllegalArgumentException("Initial chemical snapshot changed the active session");
            }
            // A reload may race the first ACK. The server then repeats the configuration snapshot for
            // this same session at a newer epoch and requires another exact ACK.
        } else if (!incomingSession.equals(sessionId)) {
            throw new IllegalArgumentException("Stale chemical snapshot session");
        }
        if (context.epoch() <= epoch) throw new IllegalArgumentException("Non-increasing chemical snapshot epoch");
        epoch = context.epoch();
        ChemicalDataContext.bind(context);
    }

    public static synchronized void disconnect() {
        disconnectCurrent(generation);
    }

    private static synchronized void disconnectCurrent(long disconnectedGeneration) {
        if (disconnectedGeneration != generation || !connectionOpen && sessionId == null && epoch == 0
              && ChemicalDataContext.current().epoch() == 0) {
            return;
        }
        connectionOpen = false;
        sessionId = null;
        epoch = 0;
        generation++;
        ChemicalDataContext.clear();
    }

    public static synchronized void stop() {
        if (stopped) return;
        stopped = true;
        long before = generation;
        disconnectCurrent(before);
        if (generation == before) {
            // Even a stop before publication invalidates any executor work captured by a live connection.
            generation++;
            ChemicalDataContext.clear();
        }
    }
}
