package mekanism.common.registration.impl;

import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registration.MekanismDeferredRegister;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric port delta: NeoForge registers entity data serializers in its own registry, while vanilla assigns the numeric
 * wire id directly through {@link EntityDataSerializers#registerSerializer}. Entries therefore use a standalone key
 * namespace for their holders and commit through {@link #registerAll()}, which the common bootstrap runs before any
 * Mekanism entity is constructed.
 */
public class DataSerializerDeferredRegister extends MekanismDeferredRegister<EntityDataSerializer<?>> {

    private static final ResourceKey<Registry<EntityDataSerializer<?>>> REGISTRY_KEY =
          ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("mekanism", "entity_data_serializers"));

    public DataSerializerDeferredRegister(String modid) {
        super(REGISTRY_KEY, modid);
    }

    public <T> MekanismDeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<T>> register(String name,
          StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        return register(name, () -> EntityDataSerializer.forValueType(codec));
    }

    public <T> MekanismDeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<ResourceKey<T>>> register(String name,
          ResourceKey<? extends Registry<T>> registryName) {
        return register(name, ResourceLocation.STREAM_CODEC.map(rl -> ResourceKey.create(registryName, rl), ResourceKey::location));
    }

    public void registerAll() {
        registerAll((registryKey, key, value) -> {
            EntityDataSerializers.registerSerializer(value);
            return value;
        });
    }
}
