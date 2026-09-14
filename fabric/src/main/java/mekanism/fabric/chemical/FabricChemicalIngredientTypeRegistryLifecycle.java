package mekanism.fabric.chemical;

import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import java.util.List;
import mekanism.api.recipes.ingredients.chemical.ChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.ChemicalIngredientTypeRegistry;
import mekanism.api.recipes.ingredients.chemical.CompoundChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.DifferenceChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.EmptyChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.IntersectionChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.SingleChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.TagChemicalIngredient;
import mekanism.common.registries.MekanismChemicalIngredientTypes;
import mekanism.fabric.registry.FabricCustomRegistryLifecycle;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Construction-thread owner of the canonical synchronized {@link ChemicalIngredient} type registry.
 *
 * <p>It allocates the single native, non-intrusive, non-defaulted vanilla registry, installs that exact instance into
 * {@link ChemicalIngredientTypeRegistry}, publishes it into the vanilla root with {@link RegistryAttribute#SYNCED}, then drives the existing
 * {@link MekanismChemicalIngredientTypes} declarations (official order) and freezes before recipe decoding. Allocation, root publication, population and the
 * mutable-to-frozen transition all belong to the construction thread; already-frozen calls are idempotent.</p>
 */
public final class FabricChemicalIngredientTypeRegistryLifecycle {

    private record BuiltIn(ResourceLocation id, MapCodec<? extends ChemicalIngredient> codec) {
    }

    private static final List<BuiltIn> BUILT_INS = List.of(
          new BuiltIn(ChemicalIngredientTypeRegistry.COMPOUND, CompoundChemicalIngredient.CODEC),
          new BuiltIn(ChemicalIngredientTypeRegistry.DIFFERENCE, DifferenceChemicalIngredient.CODEC),
          new BuiltIn(ChemicalIngredientTypeRegistry.EMPTY, EmptyChemicalIngredient.CODEC),
          new BuiltIn(ChemicalIngredientTypeRegistry.INTERSECTION, IntersectionChemicalIngredient.CODEC),
          new BuiltIn(ChemicalIngredientTypeRegistry.SINGLE, SingleChemicalIngredient.CODEC),
          new BuiltIn(ChemicalIngredientTypeRegistry.TAG, TagChemicalIngredient.CODEC));

    private static MappedRegistry<MapCodec<? extends ChemicalIngredient>> registry;
    private static FabricCustomRegistryLifecycle lifecycle = FabricCustomRegistryLifecycle.CREATED;
    private static Thread ownerThread;

    private FabricChemicalIngredientTypeRegistryLifecycle() {
    }

    public static synchronized MappedRegistry<MapCodec<? extends ChemicalIngredient>> initialize() {
        if (lifecycle == FabricCustomRegistryLifecycle.FROZEN) {
            return registry;
        }
        if (lifecycle != FabricCustomRegistryLifecycle.CREATED) {
            throw new IllegalStateException("Chemical ingredient type registry lifecycle is " + lifecycle);
        }
        ownerThread = Thread.currentThread();
        MappedRegistry<MapCodec<? extends ChemicalIngredient>> candidate =
              new MappedRegistry<>(ChemicalIngredientTypeRegistry.REGISTRY_KEY, Lifecycle.stable(), false);
        ChemicalIngredientTypeRegistry.install(candidate);
        try {
            registerRoot(candidate);
            MekanismChemicalIngredientTypes.ensureRegistered();
            verify(candidate);
            lifecycle = FabricCustomRegistryLifecycle.FROZEN;
            registry = candidate;
            return candidate;
        } catch (RuntimeException | Error failure) {
            lifecycle = FabricCustomRegistryLifecycle.FAILED;
            throw failure;
        }
    }

    public static synchronized MappedRegistry<MapCodec<? extends ChemicalIngredient>> registry() {
        MappedRegistry<MapCodec<? extends ChemicalIngredient>> value = registry;
        if (value == null) {
            throw new IllegalStateException("Chemical ingredient type registry lifecycle is not initialized");
        }
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerRoot(MappedRegistry<MapCodec<? extends ChemicalIngredient>> candidate) {
        requireOwnerThread("register the root registry");
        ResourceKey<Registry<MapCodec<? extends ChemicalIngredient>>> key = ChemicalIngredientTypeRegistry.REGISTRY_KEY;
        Registry<Registry<?>> root = (Registry) BuiltInRegistries.REGISTRY;
        if (!key.isFor(root.key())) {
            throw new IllegalArgumentException("Registry key " + key + " is not an entry of the root registry");
        }
        if (root.containsKey(key.location())) {
            throw new IllegalStateException("Root registry already contains " + key.location());
        }
        lifecycle = FabricCustomRegistryLifecycle.REGISTERING_ROOT;
        FabricRegistryBuilder<MapCodec<? extends ChemicalIngredient>, MappedRegistry<MapCodec<? extends ChemicalIngredient>>> builder =
              FabricRegistryBuilder.from(candidate);
        builder.attribute(RegistryAttribute.SYNCED);
        MappedRegistry<MapCodec<? extends ChemicalIngredient>> registered = builder.buildAndRegister();
        ResourceKey<Registry<?>> rootedKey = root.getResourceKey(candidate).orElse(null);
        if (registered != candidate || root.get(key.location()) != candidate
              || rootedKey == null || !rootedKey.location().equals(key.location())) {
            throw new IllegalStateException("Root registry did not retain the exact chemical ingredient type registry " + key.location());
        }
        RegistryAttributeHolder attributes = RegistryAttributeHolder.get(candidate);
        if (!attributes.hasAttribute(RegistryAttribute.MODDED) || !attributes.hasAttribute(RegistryAttribute.SYNCED)) {
            throw new IllegalStateException("Root registry attributes do not match " + key.location());
        }
        lifecycle = FabricCustomRegistryLifecycle.ROOT_REGISTERED;
    }

    private static void verify(MappedRegistry<MapCodec<? extends ChemicalIngredient>> candidate) {
        if (!ChemicalIngredientTypeRegistry.isFrozen() || candidate.size() != BUILT_INS.size()
              || candidate.get(ChemicalIngredientTypeRegistry.EMPTY) != EmptyChemicalIngredient.CODEC) {
            throw new IllegalStateException("Chemical ingredient type registry canonical/count invariant failed");
        }
        for (BuiltIn builtIn : BUILT_INS) {
            ResourceKey<MapCodec<? extends ChemicalIngredient>> entryKey =
                  ResourceKey.create(ChemicalIngredientTypeRegistry.REGISTRY_KEY, builtIn.id());
            MapCodec<? extends ChemicalIngredient> held = candidate.get(builtIn.id());
            if (held != builtIn.codec() || candidate.getKey(held) != builtIn.id()
                  || candidate.getHolder(entryKey).orElseThrow().value() != builtIn.codec()
                  || ChemicalIngredientTypeRegistry.idOf(builtIn.codec()) != builtIn.id()) {
                throw new IllegalStateException("Chemical ingredient type declaration identity/order failed at " + builtIn.id());
            }
        }
    }

    private static void requireOwnerThread(String operation) {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Only the construction thread may " + operation + " for "
                  + ChemicalIngredientTypeRegistry.REGISTRY_ID);
        }
    }
}
