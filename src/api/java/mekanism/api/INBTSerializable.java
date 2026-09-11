package mekanism.api;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

/**
 * Registry-aware NBT serialization contract owned by the Mekanism API.
 *
 * <p>The persistence owner supplies the registry provider. Platform attachment
 * and component lifecycles remain outside this value-level contract.</p>
 *
 * @param <T> serialized tag type
 */
public interface INBTSerializable<T extends Tag> {

    T serializeNBT(HolderLookup.Provider provider);

    void deserializeNBT(HolderLookup.Provider provider, T tag);
}
