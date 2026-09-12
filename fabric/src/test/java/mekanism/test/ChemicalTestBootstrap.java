package mekanism.test;

import java.util.List;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.common.registries.MekanismChemicals;
import mekanism.fabric.registry.FabricDefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;

public final class ChemicalTestBootstrap {
    private static RegistryAccess access;
    private ChemicalTestBootstrap() {}

    public static synchronized RegistryAccess initialize() {
        if (access != null) return access;
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FabricDefaultedRegistry<Chemical> registry = FabricDefaultedRegistry.create(ChemicalRegistry.REGISTRY_KEY, ChemicalRegistry.EMPTY_KEY, true);
        ChemicalRegistry.install(registry);
        Chemical empty = new Chemical(ChemicalBuilder.builder());
        Holder.Reference<Chemical> holder = registry.register(ChemicalRegistry.EMPTY_KEY, empty, RegistrationInfo.BUILT_IN);
        ChemicalRegistry.publishEmpty(empty, holder);
        MekanismChemicals.CHEMICALS.registerAll(registry);
        access = new RegistryAccess.ImmutableRegistryAccess(List.of(registry));
        return access;
    }
}
