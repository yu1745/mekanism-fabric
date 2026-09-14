package mekanism.common.registries;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import mekanism.api.robit.RobitSkinRegistry;
import mekanism.api.robit.AdvancementBasedRobitSkin;
import mekanism.api.robit.RobitSkin;
import mekanism.api.robit.RobitSkinSerializationHelper;
import mekanism.common.entity.RobitPrideSkinData;
import mekanism.common.registration.DeferredMapCodecRegister;
import mekanism.common.registration.DeferredMapCodecHolder;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class MekanismRobitSkins {

    private MekanismRobitSkins() {
    }

    public static final DeferredMapCodecRegister<RobitSkin> ROBIT_SKINS = new DeferredMapCodecRegister<>(RobitSkinRegistry.SERIALIZER_KEY, "mekanism");

    public static final DeferredMapCodecHolder<RobitSkin, RobitSkin> BASIC_SERIALIZER = ROBIT_SKINS.registerCodec("basic", () -> RobitSkinSerializationHelper.NETWORK_CODEC);
    public static final DeferredMapCodecHolder<RobitSkin, AdvancementBasedRobitSkin> ADVANCEMENT_BASED_SERIALIZER = ROBIT_SKINS.registerCodec("advancement_based", () -> RobitSkinSerializationHelper.ADVANCEMENT_BASED_ROBIT_SKIN_CODEC);

    public static final ResourceKey<RobitSkin> BASE = RobitSkinRegistry.BASE;
    public static final ResourceKey<RobitSkin> ALLAY = dataKey("allay");

    public static final Map<RobitPrideSkinData, ResourceKey<RobitSkin>> PRIDE_SKINS = Util.make(() -> {
        Map<RobitPrideSkinData, ResourceKey<RobitSkin>> internal = new EnumMap<>(RobitPrideSkinData.class);
        for (RobitPrideSkinData data : RobitPrideSkinData.values()) {
            internal.put(data, dataKey(data.lowerCaseName()));
        }
        return Collections.unmodifiableMap(internal);
    });

    private static ResourceKey<RobitSkin> dataKey(String name) {
        return ResourceKey.create(RobitSkinRegistry.SKIN_KEY, ResourceLocation.fromNamespaceAndPath("mekanism", name));
    }

    public static SkinLookup lookup(RegistryAccess registryAccess, ResourceKey<RobitSkin> key) {
        Registry<RobitSkin> robitSkins = registryAccess.registryOrThrow(RobitSkinRegistry.SKIN_KEY);
        Optional<RobitSkin> skin = robitSkins.getOptional(key);
        //noinspection OptionalIsPresent - Capturing lambda
        if (skin.isEmpty()) {
            return new SkinLookup(BASE, robitSkins.getOrThrow(BASE));
        }
        return new SkinLookup(key, skin.get());
    }

    public record SkinLookup(ResourceKey<RobitSkin> name, RobitSkin skin) {

        public ResourceLocation location() {
            return name.location();
        }
    }
}