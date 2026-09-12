package mekanism.fabric.datamaps;

import com.mojang.serialization.Codec;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Internal identity descriptor for one ordinary, unconditional, server-side Fabric data map.
 * Descriptor instances deliberately retain {@link Object} identity semantics.
 */
public final class FabricDataMapType<R, T> {
    private final ResourceKey<? extends Registry<R>> registryKey;
    private final ResourceLocation id;
    private final Codec<T> codec;

    public FabricDataMapType(ResourceKey<? extends Registry<R>> registryKey, ResourceLocation id, Codec<T> codec) {
        this.registryKey = Objects.requireNonNull(registryKey, "registryKey");
        this.id = Objects.requireNonNull(id, "id");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public ResourceKey<? extends Registry<R>> registryKey() {
        return registryKey;
    }

    public ResourceLocation id() {
        return id;
    }

    public Codec<T> codec() {
        return codec;
    }
}
