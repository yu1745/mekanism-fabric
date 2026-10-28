package mekanism.fabric.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mekanism.fabric.worldgen.FabricBiomeModifiers;
import net.fabricmc.fabric.impl.biome.modification.BiomeModificationImpl;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Mixin;

/** The Fabric biome API finalizes each world's dynamic registries synchronously during server construction. */
@Mixin(value = BiomeModificationImpl.class, remap = false)
abstract class BiomeModificationScopeMixin {

    @WrapMethod(method = "finalizeWorldGen", remap = false)
    private void mekanism$withWorldResources(RegistryAccess registries, Operation<Void> original) {
        FabricBiomeModifiers.withWorldModifiers(registries, () -> original.call(registries));
    }
}
