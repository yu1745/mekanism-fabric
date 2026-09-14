package mekanism.fabric.gear;

import com.mojang.serialization.Lifecycle;
import mekanism.api.gear.ModuleData;
import mekanism.api.gear.ModuleRegistry;
import mekanism.common.registries.MekanismModules;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.MappedRegistry;

/** Construction owner for the synchronized module types; allocation does not evaluate the item/gear catalog. */
public final class FabricModuleRegistry {
    private static MappedRegistry<ModuleData<?>> registry;
    private static boolean initialized;

    private FabricModuleRegistry() {
    }

    public static synchronized MappedRegistry<ModuleData<?>> allocate() {
        if (registry == null) {
            MappedRegistry<ModuleData<?>> candidate = new MappedRegistry<>(ModuleRegistry.KEY, Lifecycle.stable(), false);
            ModuleRegistry.install(candidate);
            FabricRegistryBuilder.from(candidate).attribute(RegistryAttribute.SYNCED).buildAndRegister();
            registry = candidate;
        }
        return registry;
    }

    public static synchronized void initialize() {
        if (initialized) return;
        MappedRegistry<ModuleData<?>> candidate = allocate();
        MekanismModules.MODULES.registerAll(candidate);
        candidate.freeze();
        initialized = true;
    }
}
