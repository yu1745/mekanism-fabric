package mekanism.test;

import java.util.List;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.api.recipes.ingredients.chemical.ChemicalIngredientTypeRegistry;
import mekanism.common.registries.MekanismChemicalIngredientTypes;
import mekanism.common.registries.MekanismChemicals;
import mekanism.fabric.registry.FabricDefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
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
        Chemical empty = new TrackingChemical(ChemicalBuilder.builder());
        Holder.Reference<Chemical> holder = registry.register(ChemicalRegistry.EMPTY_KEY, empty, RegistrationInfo.BUILT_IN);
        ChemicalRegistry.publishEmpty(empty, holder);
        MekanismChemicals.CHEMICALS.registerAll(registry);
        //The ordinary JVM cannot publish into the vanilla root (it is frozen by Bootstrap.bootStrap), so this worker installs the same genuine native
        //chemical-ingredient-type registry directly, mirroring how it already installs the non-root chemical registry above. Production instead publishes
        //through FabricChemicalIngredientTypeRegistryLifecycle.
        ChemicalIngredientTypeRegistry.install(new MappedRegistry<>(ChemicalIngredientTypeRegistry.REGISTRY_KEY,
              com.mojang.serialization.Lifecycle.stable(), false));
        MekanismChemicalIngredientTypes.ensureRegistered();
        access = new RegistryAccess.ImmutableRegistryAccess(List.of(registry));
        return access;
    }

    /** Test-only registered override used to prove the virtual, reentrant derivation contract. */
    public static final class TrackingChemical extends Chemical {
        private int invocations;
        private double reentrantBeforeSuper;
        private double nestedOtherChemical;
        private double reentrantAfterSuper;
        private boolean failNextDerivation;

        private TrackingChemical(ChemicalBuilder builder) {
            super(builder);
        }

        @Override
        public void updateFromDataMap(Holder<Chemical> holder) {
            invocations++;
            if (failNextDerivation) {
                failNextDerivation = false;
                throw new IllegalStateException("intentional derivation failure");
            }
            reentrantBeforeSuper = getRadioactivity();
            nestedOtherChemical = MekanismChemicals.HYDROGEN.get().getRadioactivity();
            super.updateFromDataMap(holder);
            reentrantAfterSuper = getRadioactivity();
        }

        public void armDerivationFailure() {
            failNextDerivation = true;
        }

        public void resetTracking() {
            invocations = 0;
            reentrantBeforeSuper = Double.NaN;
            nestedOtherChemical = Double.NaN;
            reentrantAfterSuper = Double.NaN;
            failNextDerivation = false;
        }

        public int invocations() { return invocations; }
        public double reentrantBeforeSuper() { return reentrantBeforeSuper; }
        public double nestedOtherChemical() { return nestedOtherChemical; }
        public double reentrantAfterSuper() { return reentrantAfterSuper; }
    }
}
