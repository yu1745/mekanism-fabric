package mekanism.common.tile.interfaces;

/**
 * Fabric port delta for NeoForge's {@code BlockEntity#onChunkUnloaded()} hook, which vanilla does not have. The
 * chunk-unload mixin invokes this for every block entity in a chunk that is being unloaded, exactly where NeoForge
 * called the overridable method.
 */
public interface IChunkUnloadListener {

    void onChunkUnloaded();
}
