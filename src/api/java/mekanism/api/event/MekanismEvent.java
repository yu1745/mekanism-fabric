package mekanism.api.event;

import mekanism.api.annotations.NothingNullByDefault;

/**
 * Fabric port delta: project-owned cancellable base for the Mekanism-owned api events, replacing the NeoForge event hierarchy
 * (the upstream event base and cancellable marker interfaces) that upstream Mekanism events were rooted in. This port owns the base type and the
 * dispatch ({@link MekanismEventBus}) because Fabric has no global mod event bus for api-level events.
 *
 * @since 10.7.0 (Fabric port)
 */
@NothingNullByDefault
public abstract class MekanismEvent {

    private boolean canceled;

    /**
     * {@return true if this event has been canceled}
     */
    public boolean isCanceled() {
        return canceled;
    }

    /**
     * Cancels or un-cancels this event; canceling carries the same meaning as on the upstream bus (the firing code aborts the action).
     */
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
