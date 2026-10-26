package mekanism.api.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fabric port delta: the dispatch point for the Mekanism-owned api events, replacing upstream's "fired on the NeoForge EVENT_BUS" contract. Listeners
 * subscribe per concrete event class and are invoked on the posting thread in registration order; {@link #post} always returns the posted event (with its
 * canceled state) exactly like the upstream bus. Dispatch is by exact class — subscribe to the concrete type the firing code documents.
 *
 * @since 10.7.0 (Fabric port)
 */
public final class MekanismEventBus {

    private static final Map<Class<?>, List<Consumer<Object>>> LISTENERS = new ConcurrentHashMap<>();

    private MekanismEventBus() {
    }

    /**
     * Registers a listener for the given concrete event type. Listener failures are not intercepted: a throwing listener aborts the dispatch, matching the
     * upstream bus where an exception in one listener propagates to the firing code.
     */
    public static <E extends MekanismEvent> void addListener(Class<E> type, Consumer<? super E> listener) {
        LISTENERS.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Posts the event to the listeners registered for its exact class.
     *
     * @return the same event instance, after all listeners ran; check {@link MekanismEvent#isCanceled()} to decide whether to abort.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <E extends MekanismEvent> E post(E event) {
        List<Consumer<Object>> listeners = LISTENERS.get(event.getClass());
        if (listeners != null) {
            for (Consumer<Object> listener : listeners) {
                listener.accept(event);
            }
        }
        return event;
    }
}
