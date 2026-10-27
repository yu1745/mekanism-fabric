package mekanism.api.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fabric port delta: the dispatch point for the Mekanism-owned api events, replacing upstream's "fired on the NeoForge EVENT_BUS" contract. Listeners
 * subscribe to an event class and are invoked on the posting thread in registration order; {@link #post} always returns the posted event (with its
 * canceled state). A listener also receives instances of subclasses, as with the upstream event hierarchy.
 *
 * @since 10.7.0 (Fabric port)
 */
public final class MekanismEventBus {

    private record Listener(Class<? extends MekanismEvent> type, Consumer<Object> consumer) {
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private MekanismEventBus() {
    }

    /**
     * Registers a listener for the given event type and its subclasses. Listener failures are not intercepted: a throwing listener aborts the dispatch, matching the
     * upstream bus where an exception in one listener propagates to the firing code.
     */
    @SuppressWarnings("unchecked")
    public static <E extends MekanismEvent> void addListener(Class<E> type, Consumer<? super E> listener) {
        LISTENERS.add(new Listener(Objects.requireNonNull(type), (Consumer<Object>) Objects.requireNonNull(listener)));
    }

    /**
     * Posts the event to listeners registered for its class or a superclass.
     *
     * @return the same event instance, after all listeners ran; check {@link MekanismEvent#isCanceled()} to decide whether to abort.
     */
    public static <E extends MekanismEvent> E post(E event) {
        Objects.requireNonNull(event);
        for (Listener listener : LISTENERS) {
            if (listener.type().isInstance(event)) {
                listener.consumer().accept(event);
            }
        }
        return event;
    }
}
