package mekanism.api.datamaps;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Platform-neutral identity descriptor for one Mekanism data map. */
public final class MekanismDataMapType<R, T> {
    private final ResourceKey<? extends Registry<R>> registryKey;
    private final ResourceLocation id;
    private final Codec<T> codec;
    private final StreamCodec<RegistryFriendlyByteBuf, T> networkCodec;

    public MekanismDataMapType(ResourceKey<? extends Registry<R>> registryKey, ResourceLocation id, Codec<T> codec) {
        this(registryKey, id, codec, null);
    }

    public MekanismDataMapType(ResourceKey<? extends Registry<R>> registryKey, ResourceLocation id, Codec<T> codec,
          StreamCodec<RegistryFriendlyByteBuf, T> networkCodec) {
        this.registryKey = Objects.requireNonNull(registryKey, "registryKey");
        this.id = Objects.requireNonNull(id, "id");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.networkCodec = networkCodec;
    }

    public ResourceKey<? extends Registry<R>> registryKey() { return registryKey; }
    public ResourceLocation id() { return id; }
    public Codec<T> codec() { return codec; }
    public Optional<StreamCodec<RegistryFriendlyByteBuf, T>> networkCodec() { return Optional.ofNullable(networkCodec); }
    public boolean synchronizedToClient() { return networkCodec != null; }
}
