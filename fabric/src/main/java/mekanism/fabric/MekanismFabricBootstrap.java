package mekanism.fabric;

import mekanism.fabric.registry.MekanismFabricGameEvents;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimal Java entrypoint for the incremental Mekanism Fabric port. */
public final class MekanismFabricBootstrap implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/Fabric");
    private static final ResourceLocation SEISMIC_VIBRATION =
          ResourceLocation.fromNamespaceAndPath("mekanism", "seismic_vibration");

    @Override
    public void onInitialize() {
        MekanismFabricGameEvents.REGISTER.registerAll((registryKey, key, value) ->
              Registry.register(BuiltInRegistries.GAME_EVENT, key.location(), value));

        GameEvent held = MekanismFabricGameEvents.SEISMIC_VIBRATION.get();
        GameEvent registered = BuiltInRegistries.GAME_EVENT.get(SEISMIC_VIBRATION);
        if (registered != held || !SEISMIC_VIBRATION.equals(MekanismFabricGameEvents.SEISMIC_VIBRATION.key().location())
              || !SEISMIC_VIBRATION.equals(BuiltInRegistries.GAME_EVENT.getKey(held))) {
            throw new IllegalStateException("Fabric registry proof did not bind the canonical seismic vibration game event");
        }
        LOGGER.info("MEKANISM_FABRIC_REGISTRY_PROOF key={} canonical_identity=true", SEISMIC_VIBRATION);
    }
}
