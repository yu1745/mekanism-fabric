package mekanism.fabric.robit;

import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import mekanism.api.robit.RobitSkin;
import mekanism.api.robit.RobitSkinRegistry;
import mekanism.api.robit.RobitSkinSerializationHelper;
import mekanism.common.registries.MekanismRobitSkins;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.MappedRegistry;

/** Static serializer construction is separate from codec population to avoid the DIRECT_CODEC class-init cycle. */
public final class FabricRobitRegistries {
    private static MappedRegistry<MapCodec<? extends RobitSkin>> serializers;
    private static boolean initialized;

    private FabricRobitRegistries() {
    }

    public static synchronized MappedRegistry<MapCodec<? extends RobitSkin>> allocate() {
        if (serializers == null) {
            MappedRegistry<MapCodec<? extends RobitSkin>> candidate = new MappedRegistry<>(RobitSkinRegistry.SERIALIZER_KEY, Lifecycle.stable(), false);
            RobitSkinRegistry.install(candidate);
            // Official serializer registry is NOT synchronized; the dynamic skin values are synchronized below.
            FabricRegistryBuilder.from(candidate).buildAndRegister();
            serializers = candidate;
        }
        return serializers;
    }

    public static synchronized void initialize() {
        if (initialized) return;
        MappedRegistry<MapCodec<? extends RobitSkin>> registry = allocate();
        // Force the complete helper initialization before evaluating any deferred codec supplier.
        var dataCodec = RobitSkinSerializationHelper.DIRECT_CODEC;
        var networkCodec = RobitSkinSerializationHelper.NETWORK_CODEC.codec();
        MekanismRobitSkins.ROBIT_SKINS.registerAll(registry);
        registry.freeze();
        DynamicRegistries.registerSynced(RobitSkinRegistry.SKIN_KEY, dataCodec, networkCodec);
        initialized = true;
    }
}
