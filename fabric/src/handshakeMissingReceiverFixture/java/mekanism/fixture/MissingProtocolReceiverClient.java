package mekanism.fixture;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client fixture that mirrors synced registry IDs but deliberately declares no Mekanism payloads. */
public final class MissingProtocolReceiverClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/HandshakeMissingReceiverFixture");

    @Override
    public void onInitializeClient() {
        registerGameEvent("seismic_vibration", 64);
        registerGameEvent("jetpack_burn", 16);
        registerGameEvent("gravity_modulate", 16);
        registerGameEvent("gravity_modulate_boosted", 32);
        LOGGER.info("MEKANISM_FIXTURE_MISSING_RECEIVER_READY registry_entries=4 payloads=0");
    }

    private static void registerGameEvent(String path, int radius) {
        Registry.register(BuiltInRegistries.GAME_EVENT,
              ResourceLocation.fromNamespaceAndPath("mekanism", path), new GameEvent(radius));
    }
}
