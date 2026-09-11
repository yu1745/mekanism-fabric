package mekanism.fabric.registry;

/** Lifecycle of one Mekanism-specific deferred registry batch. */
public enum FabricRegistryLifecycle {
    COLLECTING,
    REGISTERING,
    REGISTERED,
    FAILED
}
