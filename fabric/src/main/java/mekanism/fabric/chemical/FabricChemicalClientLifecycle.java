package mekanism.fabric.chemical;

import java.util.Objects;
import mekanism.api.chemical.ChemicalDataContext;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Physical-client owner for name-based chemical snapshots and reconnect cleanup. */
public final class FabricChemicalClientLifecycle {
    private static String sessionId;
    private static long epoch;
    private static long generation;
    private static boolean initialized;
    private static boolean stopped;

    private FabricChemicalClientLifecycle() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(FabricChemicalClientLifecycle::disconnect));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stop());
        initialized = true;
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
            if (sessionId != null && sessionId.equals(incomingSession)) throw new IllegalArgumentException("Duplicate initial chemical snapshot");
            sessionId = incomingSession;
            epoch = 0;
        } else if (!incomingSession.equals(sessionId)) {
            throw new IllegalArgumentException("Stale chemical snapshot session");
        }
        if (context.epoch() <= epoch) throw new IllegalArgumentException("Non-increasing chemical snapshot epoch");
        epoch = context.epoch();
        ChemicalDataContext.bind(context);
    }

    public static synchronized void disconnect() {
        sessionId = null;
        epoch = 0;
        generation++;
        ChemicalDataContext.clear();
    }

    public static synchronized void stop() {
        stopped = true;
        disconnect();
    }
}
