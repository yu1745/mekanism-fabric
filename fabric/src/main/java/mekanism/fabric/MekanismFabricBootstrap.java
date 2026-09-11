package mekanism.fabric;

import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registries.MekanismGameEvents;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimal Java entrypoint for the incremental Mekanism Fabric port. */
public final class MekanismFabricBootstrap implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/Fabric");

    @Override
    public void onInitialize() {
        MekanismGameEvents.GAME_EVENTS.registerAll(BuiltInRegistries.GAME_EVENT);

        verifyGameEvent(MekanismGameEvents.SEISMIC_VIBRATION, "seismic_vibration", 64);
        verifyGameEvent(MekanismGameEvents.JETPACK_BURN, "jetpack_burn", 16);
        verifyGameEvent(MekanismGameEvents.GRAVITY_MODULATE, "gravity_modulate", 16);
        verifyGameEvent(MekanismGameEvents.GRAVITY_MODULATE_BOOSTED, "gravity_modulate_boosted", 32);
        LOGGER.info("MEKANISM_FABRIC_REGISTRY_PROOF key=mekanism:seismic_vibration canonical_identity=true entries=4");
    }

    @SuppressWarnings("deprecation")
    private static void verifyGameEvent(MekanismDeferredHolder<GameEvent, GameEvent> holder, String path, int radius) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mekanism", path);
        GameEvent held = holder.get();
        GameEvent registered = BuiltInRegistries.GAME_EVENT.get(id);
        Holder<GameEvent> canonicalHolder = BuiltInRegistries.GAME_EVENT.getHolderOrThrow(holder.getKey());
        if (registered != held || !id.equals(holder.getId()) || !id.equals(BuiltInRegistries.GAME_EVENT.getKey(held))
              || held.notificationRadius() != radius || !holder.isBound() || canonicalHolder.value() != held || !holder.is(canonicalHolder)) {
            throw new IllegalStateException("Fabric registry proof failed for " + id);
        }
    }
}
