package mekanism.fabric.chemical;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.api.text.EnumColor;
import mekanism.common.registries.MekanismChemicals;
import mekanism.common.resource.PrimaryResource;
import mekanism.fabric.registry.FabricDefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Construction-thread owner of Mekanism's synchronized intrusive/defaulted chemical registry. */
public final class FabricChemicalRegistryLifecycle {
    private static FabricDefaultedRegistry<Chemical> registry;
    private static boolean initialized;

    private FabricChemicalRegistryLifecycle() {
    }

    /** Allocation and EMPTY publication only: safe before the API service loader and content catalog initialize. */
    public static synchronized FabricDefaultedRegistry<Chemical> allocate() {
        if (registry != null) return registry;
        FabricDefaultedRegistry<Chemical> candidate = FabricDefaultedRegistry.create(
              ChemicalRegistry.REGISTRY_KEY, ChemicalRegistry.EMPTY_KEY, true);
        ChemicalRegistry.install(candidate);
        candidate.registerRoot();
        Chemical empty = new Chemical(ChemicalBuilder.builder());
        Holder.Reference<Chemical> emptyHolder = candidate.register(ChemicalRegistry.EMPTY_KEY, empty, RegistrationInfo.BUILT_IN);
        ChemicalRegistry.publishEmpty(empty, emptyHolder);
        registry = candidate;
        return candidate;
    }

    public static synchronized FabricDefaultedRegistry<Chemical> initialize() {
        FabricDefaultedRegistry<Chemical> candidate = allocate();
        if (initialized) return candidate;
        Chemical empty = ChemicalRegistry.empty();
        Holder.Reference<Chemical> emptyHolder = ChemicalRegistry.emptyHolder();
        MekanismChemicals.CHEMICALS.registerAll(candidate);
        verify(candidate, empty, emptyHolder);
        // Freeze on the construction thread. Later RegistryAccess snapshots may call freeze
        // idempotently from reload workers, but no population is permitted after publication.
        candidate.freeze();
        initialized = true;
        return candidate;
    }

    private static void verify(FabricDefaultedRegistry<Chemical> candidate, Chemical empty, Holder.Reference<Chemical> emptyHolder) {
        if (candidate.size() != 65 || candidate.get(ChemicalRegistry.EMPTY_KEY.location()) != empty
              || candidate.getId(empty) != 0 || candidate.canonicalDefaultHolder() != emptyHolder || ChemicalRegistry.empty() != empty
              || ChemicalRegistry.emptyHolder() != emptyHolder) {
            throw new IllegalStateException("Chemical registry canonical/default/count invariant failed");
        }
        List<String> expected = new ArrayList<>(List.of("bio", "fungi", "tin", "gold", "refined_obsidian", "diamond", "redstone", "carbon",
              "hydrogen", "oxygen", "steam", "water_vapor", "chlorine", "sulfur_dioxide", "sulfur_trioxide", "sulfuric_acid",
              "hydrogen_chloride", "hydrofluoric_acid", "uranium_oxide", "uranium_hexafluoride", "ethene", "sodium",
              "superheated_sodium", "brine", "lithium", "osmium", "fissile_fuel", "nuclear_waste", "spent_nuclear_waste",
              "plutonium", "polonium", "antimatter"));
        for (EnumColor color : EnumColor.values()) expected.add(color.getRegistryPrefix());
        for (PrimaryResource resource : PrimaryResource.values()) {
            expected.add("dirty_" + resource.getRegistrySuffix());
            expected.add("clean_" + resource.getRegistrySuffix());
        }
        if (expected.size() != 64) throw new IllegalStateException("Expected exactly 64 non-empty chemical declarations");
        for (int index = 0; index < expected.size(); index++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mekanism", expected.get(index));
            ResourceKey<Chemical> key = ResourceKey.create(ChemicalRegistry.REGISTRY_KEY, id);
            Holder.Reference<Chemical> holder = candidate.getHolder(key).orElse(null);
            if (holder == null || holder.value() != candidate.getOptional(id).orElse(null)
                  || candidate.getId(holder.value()) != index + 1) {
                throw new IllegalStateException("Chemical declaration order/holder invariant failed at " + id);
            }
        }
    }

    public static FabricDefaultedRegistry<Chemical> registry() {
        FabricDefaultedRegistry<Chemical> value = registry;
        if (value == null) throw new IllegalStateException("Chemical registry lifecycle is not initialized");
        return value;
    }
}
