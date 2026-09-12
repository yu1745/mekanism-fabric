package mekanism.fabric.registry;

/** Lifecycle of one intrusive Fabric custom registry owned by the construction thread. */
public enum FabricCustomRegistryLifecycle {
    CREATED,
    REGISTERING_ROOT,
    ROOT_REGISTERED,
    FREEZING,
    FROZEN,
    FAILED
}
