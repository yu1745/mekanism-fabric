package mekanism.fabric;

import mekanism.api.heat.HeatAPI;
import mekanism.common.config.MekanismStartupConfig;
import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registries.MekanismGameEvents;
import mekanism.fabric.config.FabricMekanismConfigManager;
import mekanism.fabric.network.FabricMekanismNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimal Java entrypoint for the incremental Mekanism Fabric port. */
public final class MekanismFabricBootstrap implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/Fabric");
    private static final FabricMekanismConfigManager CONFIG_MANAGER = FabricMekanismConfigManager.createDefault();
    private static final MekanismStartupConfig STARTUP_CONFIG = new MekanismStartupConfig();

    @Override
    public void onInitialize() {
        FabricMekanismConfigManager.Registration startup = CONFIG_MANAGER.registerAndLoad(STARTUP_CONFIG);
        verifyStartupConfig(startup);
        FabricMekanismNetworking.Registration networking = FabricMekanismNetworking.initialize();
        LOGGER.info("MEKANISM_FABRIC_NETWORK_PROOF protocol={} phase=configuration codecs={} server_receiver={} client_required={}",
              networking.protocolVersion(), networking.codecs(), networking.serverReceiver(), networking.clientRequired());
        MekanismGameEvents.GAME_EVENTS.registerAll(BuiltInRegistries.GAME_EVENT);

        verifyGameEvent(MekanismGameEvents.SEISMIC_VIBRATION, "seismic_vibration", 64);
        verifyGameEvent(MekanismGameEvents.JETPACK_BURN, "jetpack_burn", 16);
        verifyGameEvent(MekanismGameEvents.GRAVITY_MODULATE, "gravity_modulate", 16);
        verifyGameEvent(MekanismGameEvents.GRAVITY_MODULATE_BOOSTED, "gravity_modulate_boosted", 32);
        LOGGER.info("MEKANISM_FABRIC_REGISTRY_PROOF key=mekanism:seismic_vibration canonical_identity=true entries=4");
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            double ambient = HeatAPI.getAmbientTemp(server.overworld(), BlockPos.ZERO);
            if (!Double.isFinite(ambient) || ambient <= 0) {
                throw new IllegalStateException("Fabric positional ambient-temperature proof failed: " + ambient);
            }
            LOGGER.info("MEKANISM_FABRIC_HEAT_PROOF positional=true ambient_finite=true");
        });
    }

    private static void verifyStartupConfig(FabricMekanismConfigManager.Registration registration) {
        boolean valid = STARTUP_CONFIG.isLoaded() && STARTUP_CONFIG.getConfigSpec().size() == 12
              && STARTUP_CONFIG.armoredFreeRunnerArmor.get() >= 0
              && STARTUP_CONFIG.armoredFreeRunnerToughness.get() >= 0
              && STARTUP_CONFIG.armoredFreeRunnerKnockbackResistance.get() >= 0
              && STARTUP_CONFIG.armoredJetpackArmor.get() >= 0
              && STARTUP_CONFIG.armoredJetpackToughness.get() >= 0
              && STARTUP_CONFIG.armoredJetpackKnockbackResistance.get() >= 0
              && STARTUP_CONFIG.mekaSuitHelmetArmor.get() >= 0
              && STARTUP_CONFIG.mekaSuitBodyArmorArmor.get() >= 0
              && STARTUP_CONFIG.mekaSuitPantsArmor.get() >= 0
              && STARTUP_CONFIG.mekaSuitBootsArmor.get() >= 0
              && STARTUP_CONFIG.mekaSuitToughness.get() >= 0
              && STARTUP_CONFIG.mekaSuitKnockbackResistance.get() >= 0;
        if (!valid) {
            throw new IllegalStateException("Fabric startup config proof failed for " + registration.file());
        }
        LOGGER.info("MEKANISM_FABRIC_CONFIG_PROOF file=startup.toml values=12 loaded=true created={} corrected={}",
              registration.loadResult().created(), registration.loadResult().correctedValues());
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
