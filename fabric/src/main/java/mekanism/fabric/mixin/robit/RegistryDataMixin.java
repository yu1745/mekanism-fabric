package mekanism.fabric.mixin.robit;

import com.mojang.serialization.Lifecycle;
import mekanism.api.robit.RobitSkinRegistry;
import mekanism.fabric.robit.RobitSkinMappedRegistry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only Mekanism's robit datapack registry has a default key. Never changes another registry's construction. */
@Mixin(targets = "net.minecraft.resources.RegistryDataLoader$RegistryData")
public abstract class RegistryDataMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "create", at = @At(value = "NEW", target = "(Lnet/minecraft/resources/ResourceKey;Lcom/mojang/serialization/Lifecycle;)Lnet/minecraft/core/MappedRegistry;"), require = 1)
    private <T> MappedRegistry<T> mekanism$defaultRobitRegistry(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle) {
        if (key.equals(RobitSkinRegistry.SKIN_KEY)) {
            return (MappedRegistry) new RobitSkinMappedRegistry(lifecycle);
        }
        return new MappedRegistry<>(key, lifecycle);
    }
}
