package mekanism.fabric;

import mekanism.api.MekanismServices;
import mekanism.api.heat.HeatAPI;
import mekanism.api.recipes.ingredients.creator.IIngredientCreatorProvider;
import mekanism.common.config.MekanismStartupConfig;
import mekanism.fabric.chemical.FabricChemicalLifecycle;
import mekanism.fabric.config.FabricChemicalConfig;
import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registries.MekanismGameEvents;
import mekanism.api.MekanismServerLifecycle;
import mekanism.common.lib.MekanismServerState;
import mekanism.common.lib.frequency.FrequencyManager;
import mekanism.common.lib.multiblock.MultiblockManager;
import mekanism.common.content.qio.QIOGlobalItemLookup;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import mekanism.fabric.config.FabricMekanismConfigManager;
import mekanism.fabric.datamaps.FabricMekanismDataMaps;
import mekanism.fabric.network.FabricMekanismNetworking;
import mekanism.fabric.network.FabricChemicalNetworking;
import mekanism.fabric.network.FabricContainerNetworking;
import mekanism.fabric.network.FabricTileUpdateNetworking;
import mekanism.fabric.MekanismUsernameLifecycle;
import mekanism.fabric.recipe.FabricIngredientCreatorProvider;
import mekanism.fabric.recipe.FabricRecipeCacheLifecycle;
import mekanism.fabric.recipe.FabricRecipeRegistration;
import mekanism.fabric.tile.FabricCapabilityTileLifecycle;
import mekanism.common.registries.MekanismChemicalIngredientTypes;
import mekanism.fabric.chemical.FabricChemicalIngredientTypeRegistryLifecycle;
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
        //Task052 Q4 lifecycle owner: capture the starting server (no overworld access here), and on the post-save SERVER_STOPPED tail run the domain
        // cleanup in the original Mekanism.serverStopped order before releasing the shared server reference for the same server instance only
        ServerLifecycleEvents.SERVER_STARTING.register(MekanismServerLifecycle::captureStartingServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            //Guard BOTH the domain cleanup and the release by same-server identity: a stale stop event belonging to a different (older)
            // server instance must not wipe state owned by the live server. Cleanup still runs before release, and release happens even
            // when the cleanup throws, so a failed cleanup does not pin a dead server reference.
            if (MekanismServerLifecycle.getServer() == server) {
                try {
                    MekanismServerState.clearServerState();
                } finally {
                    MekanismServerLifecycle.releaseStoppedServer(server);
                }
            }
        });
        //World LOAD is after the world is installed in the server's level map. Never load SavedData at SERVER_STARTING.
        ServerWorldEvents.LOAD.register((server, world) -> {
            FrequencyManager.load();
            MultiblockManager.createOrLoadAll();
            QIOGlobalItemLookup.INSTANCE.createOrLoad();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            FrequencyManager.tick(server.tickRateManager().runsNormally());
            mekanism.common.lib.transmitter.TransmitterNetworkRegistry.onTick(server);
            //Original LOWEST post-tick pass: flush masters dirtied by later valves even while normal ticking is frozen.
            MultiblockManager.endOfTickAll();
        });
        FabricMekanismConfigManager.Registration startup = CONFIG_MANAGER.registerAndLoad(STARTUP_CONFIG);
        verifyStartupConfig(startup);
        FabricChemicalConfig.initializeCommon(CONFIG_MANAGER);
        FabricChemicalLifecycle.initialize();
        FabricChemicalIngredientTypeRegistryLifecycle.initialize();
        mekanism.fabric.robit.FabricRobitRegistries.initialize();
        FabricRecipeCacheLifecycle.initialize();
        verifyIngredientCreatorService();
        verifyRecipeRegistration();
        FabricMekanismNetworking.Registration networking = FabricMekanismNetworking.initialize();
        FabricChemicalNetworking.initialize();
        FabricTileUpdateNetworking.initialize();
        FabricContainerNetworking.initialize();
        mekanism.fabric.radiation.FabricRadiationLifecycle.initialize();
        FabricMekanismDataMaps.initializeCallbacks();
        FabricCapabilityTileLifecycle.initialize();
        mekanism.fabric.registry.FabricMekanismBlockRegistries.initialize();
        MekanismUsernameLifecycle.initialize();
        LOGGER.info("MEKANISM_FABRIC_NETWORK_PROOF protocol={} phase=configuration codecs={} server_receiver={} client_required={}",
              networking.protocolVersion(), networking.codecs(), networking.serverReceiver(), networking.clientRequired());
        MekanismGameEvents.GAME_EVENTS.registerAll(BuiltInRegistries.GAME_EVENT);
        mekanism.common.registries.MekanismParticleTypes.PARTICLE_TYPES.registerAll(BuiltInRegistries.PARTICLE_TYPE);
        mekanism.common.advancements.MekanismCriteriaTriggers.CRITERIA_TRIGGERS.registerAll(BuiltInRegistries.TRIGGER_TYPES);

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

    private static void verifyRecipeRegistration() {
        //Note: Recipe type and serializer registration runs in the same startup window as the game event registration below, before the built-in registries stop accepting writes.
        FabricRecipeRegistration.register();
        int recipeTypes = 0;
        int serializers = 0;
        for (var holder : mekanism.api.recipes.MekanismRecipeTypes.recipeTypeHolders()) {
            if (!holder.isBound() || !BuiltInRegistries.RECIPE_TYPE.containsKey(holder.getId())) {
                throw new IllegalStateException("Mekanism recipe type was not registered: " + holder.getId());
            }
            if (BuiltInRegistries.RECIPE_TYPE.getHolderOrThrow(
                  net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE_TYPE, holder.getId())).value() != holder.value()) {
                throw new IllegalStateException("Mekanism recipe type registration did not reuse the declared instance: " + holder.getId());
            }
            recipeTypes++;
        }
        for (String entry : java.util.List.of("crushing", "enriching", "smelting", "chemical_infusing", "combining", "separating", "washing", "evaporating",
              "activating", "centrifuging", "crystallizing", "dissolution", "compressing", "purifying", "injecting", "nucleosynthesizing",
              "energy_conversion", "chemical_conversion", "oxidizing", "pigment_extracting", "pigment_mixing", "metallurgic_infusing", "painting", "reaction",
              "rotary", "sawing")) {
            if (!BuiltInRegistries.RECIPE_SERIALIZER.containsKey(ResourceLocation.fromNamespaceAndPath("mekanism", entry))) {
                throw new IllegalStateException("Mekanism recipe serializer was not registered: " + entry);
            }
            serializers++;
        }
        LOGGER.info("MEKANISM_FABRIC_RECIPE_REGISTRATION_PROOF recipe_types={} serializers={} identity_preserved=true", recipeTypes, serializers);
    }

    private static void verifyIngredientCreatorService() {
        IIngredientCreatorProvider provider = MekanismServices.load(IIngredientCreatorProvider.class);
        if (provider.itemStackIngredientCreator() == null || provider.fluidStackIngredientCreator() == null
              || provider.chemicalStackIngredientCreator() == null || provider.chemicalIngredientCreator() == null) {
            throw new IllegalStateException("Fabric ingredient creator service proof failed for " + provider.getClass().getName());
        }
        LOGGER.info("MEKANISM_FABRIC_INGREDIENT_PROOF service={} chemical_types_frozen={}",
              provider.getClass().getName(), mekanism.api.recipes.ingredients.chemical.ChemicalIngredientTypeRegistry.isFrozen());
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
