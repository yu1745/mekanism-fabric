package mekanism.fabric.mixin.registry;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The five legacy aliases declared by the fixed official Mekanism catalogs, not a general registry adapter. */
@Mixin(MappedRegistry.class)
public abstract class LegacyRegistryAliasMixin<T> {
    @Shadow @Final private Map<ResourceLocation, Holder.Reference<T>> byLocation;
    @Shadow @Final private ResourceKey<? extends Registry<T>> key;

    @Unique
    private @Nullable ResourceLocation mekanism$resolveLegacy(@Nullable ResourceLocation name) {
        if (name == null || byLocation.containsKey(name) || !name.getNamespace().equals("mekanism")) {
            return name;
        }
        String path = name.getPath();
        if (key.equals(Registries.ITEM) && path.equals("upgrade_gas")) {
            return ResourceLocation.fromNamespaceAndPath("mekanism", "upgrade_chemical");
        }
        if (key.equals(Registries.DATA_COMPONENT_TYPE) &&
              (path.equals("gases") || path.equals("infuse_types") || path.equals("pigments") || path.equals("slurries"))) {
            return ResourceLocation.fromNamespaceAndPath("mekanism", "chemicals");
        }
        return name;
    }

    @Unique
    private @Nullable ResourceKey<T> mekanism$resolveLegacyKey(@Nullable ResourceKey<T> original) {
        if (original == null || !original.isFor(key)) {
            return original;
        }
        ResourceLocation resolved = mekanism$resolveLegacy(original.location());
        return resolved.equals(original.location()) ? original : ResourceKey.create(key, resolved);
    }

    @ModifyVariable(method = {"get(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;",
          "getHolder(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"}, at = @At("HEAD"), argsOnly = true)
    private ResourceLocation mekanism$resolveLocation(ResourceLocation name) {
        return mekanism$resolveLegacy(name);
    }

    @ModifyVariable(method = {"get(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;",
          "getHolder(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;",
          "getOrCreateHolderOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;"},
          at = @At("HEAD"), argsOnly = true)
    private ResourceKey<T> mekanism$resolveKey(ResourceKey<T> original) {
        return mekanism$resolveLegacyKey(original);
    }
}
