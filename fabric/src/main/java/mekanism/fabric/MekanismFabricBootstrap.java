package mekanism.fabric;

import mekanism.api.MekanismAPI;
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
import mekanism.fabric.network.FabricServerPlayNetworking;
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
import java.util.List;

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
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            net.minecraft.server.level.ServerPlayer disconnected = handler.player;
            server.execute(() -> mekanism.common.content.gear.mekasuit.MekaSuitFlight.release(disconnected));
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
              mekanism.common.content.gear.mekasuit.MekaSuitFlight.respawn(oldPlayer, newPlayer));
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
        //Original commonSetup transplant: holiday state starts with the config owner available and wraps filterable sounds once, at startup
        mekanism.common.base.holiday.HolidayManager.init();
        FabricChemicalLifecycle.initialize();
        FabricChemicalIngredientTypeRegistryLifecycle.initialize();
        mekanism.fabric.robit.FabricRobitRegistries.initialize();
        FabricRecipeCacheLifecycle.initialize();
        verifyIngredientCreatorService();
        verifyRecipeRegistration();
        FabricMekanismNetworking.Registration networking = FabricMekanismNetworking.initialize();
        FabricServerPlayNetworking.initialize();
        FabricChemicalNetworking.initialize();
        FabricTileUpdateNetworking.initialize();
        FabricContainerNetworking.initialize();
        mekanism.fabric.network.FabricTransmitterNetworking.initialize();
        mekanism.fabric.network.FabricGearEffectsNetworking.initialize();
        mekanism.fabric.network.FabricSecurityNetworking.initialize();
        mekanism.fabric.radiation.FabricRadiationLifecycle.initialize();
        FabricMekanismDataMaps.initializeCallbacks();
        FabricCapabilityTileLifecycle.initialize();
        //Canonical content catalog commit. The catalogs declare into shared registers, so their static initializers must run
        //before the single registration window; data components and armor materials are dependencies of item factories.
        mekanism.common.registries.MekanismDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
        mekanism.common.registries.MekanismArmorMaterials.ARMOR_MATERIALS.registerAll(BuiltInRegistries.ARMOR_MATERIAL);
        mekanism.common.registries.MekanismSounds.SOUND_EVENTS.registerAll(BuiltInRegistries.SOUND_EVENT);
        mekanism.common.registries.MekanismItems.ITEMS.registerAll(BuiltInRegistries.ITEM);
        //Force the shared block/type catalogs to declare before the platform register commits them.
        mekanism.common.registries.MekanismBlocks.PROCESSED_RESOURCE_BLOCKS.size();
        mekanism.common.registries.MekanismTileEntityTypes.TILE_ENTITY_TYPES.getEntries();
        mekanism.fabric.registry.FabricMekanismBlockRegistries.initialize();
        mekanism.common.registries.MekanismContainerTypes.CONTAINER_TYPES.registerAll(BuiltInRegistries.MENU);
        mekanism.common.registries.MekanismCreativeTabs.CREATIVE_TABS.registerAll(BuiltInRegistries.CREATIVE_MODE_TAB);
        mekanism.common.item.loot.MekanismLootFunctions.REGISTER.registerAll(BuiltInRegistries.LOOT_FUNCTION_TYPE);
        mekanism.common.item.predicate.MekanismItemPredicates.PREDICATES.registerAll(BuiltInRegistries.ITEM_SUB_PREDICATE_TYPE);
        mekanism.fabric.item.FabricCreativeTabContents.initialize();
        //Fluid families: still/flowing fluids first (the liquid block resolves the registered flowing fluid), then the
        //liquid blocks and buckets. Bucket dispense behavior mirrors the official common-setup pass.
        mekanism.common.registries.MekanismFluids.FLUIDS.registerAll(BuiltInRegistries.FLUID, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
        mekanism.common.registries.MekanismFluids.FLUIDS.registerBucketDispenserBehavior();
        //Module data is a Mekanism-owned synced registry; module items must exist before the module data evaluates its item holders.
        mekanism.fabric.gear.FabricModuleRegistry.initialize();
        LOGGER.info("MEKANISM_FABRIC_CONTENT_PROOF blocks={} items={} block_entities={} menus={} fluids={} sounds={} data_components={} armor_materials={} modules={} creative_tabs={}",
              mekanismCount(BuiltInRegistries.BLOCK), mekanismCount(BuiltInRegistries.ITEM), mekanismCount(BuiltInRegistries.BLOCK_ENTITY_TYPE),
              mekanismCount(BuiltInRegistries.MENU), mekanismCount(BuiltInRegistries.FLUID), mekanismCount(BuiltInRegistries.SOUND_EVENT),
              mekanismCount(BuiltInRegistries.DATA_COMPONENT_TYPE), mekanismCount(BuiltInRegistries.ARMOR_MATERIAL),
              mekanism.api.MekanismAPI.MODULE_REGISTRY.size(), mekanismCount(BuiltInRegistries.CREATIVE_MODE_TAB));
        MekanismUsernameLifecycle.initialize();
        LOGGER.info("MEKANISM_FABRIC_NETWORK_PROOF protocol={} phase=configuration codecs={} server_receiver={} client_required={}",
              networking.protocolVersion(), networking.codecs(), networking.serverReceiver(), networking.clientRequired());
        //Entity data serializers are not a vanilla registry: numeric ids are assigned directly, before any entity exists.
        mekanism.common.registries.MekanismDataSerializers.DATA_SERIALIZERS.registerAll();
        mekanism.common.registries.MekanismEntityTypes.ENTITY_TYPES.registerAll(BuiltInRegistries.ENTITY_TYPE);
        mekanism.common.CommonWorldTickHandler.register();
        //NeoForge's IItemExtension#onItemUseFirst hook: vanilla fires no per-item pre-block-use callback, so the Fabric
        //use-block event forwards to items implementing the Mekanism surface at the same point.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof mekanism.common.item.interfaces.IItemUseFirst first) {
                return first.onItemUseFirst(held, new net.minecraft.world.item.context.UseOnContext(player, hand, hitResult));
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        //NeoForge's Entity#onRemovedFromLevel hook: the robit registers its follow-up chunk ticket at entity unload.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof mekanism.common.entity.EntityRobit robit) {
                robit.onRemovedFromLevel();
            }
        });
        MekanismGameEvents.GAME_EVENTS.registerAll(BuiltInRegistries.GAME_EVENT);
        mekanism.common.registries.MekanismParticleTypes.PARTICLE_TYPES.registerAll(BuiltInRegistries.PARTICLE_TYPE);
        mekanism.common.advancements.MekanismCriteriaTriggers.CRITERIA_TRIGGERS.registerAll(BuiltInRegistries.TRIGGER_TYPES);
        //Official worldgen: the custom feature/provider codecs must exist before the datapack decodes the packaged
        //configured/placed features, and the biome modifier adapter must register before biomes load.
        mekanism.common.registries.MekanismFeatures.FEATURES.registerAll(BuiltInRegistries.FEATURE);
        mekanism.common.registries.MekanismIntProviderTypes.INT_PROVIDER_TYPES.registerAll(BuiltInRegistries.INT_PROVIDER_TYPE);
        mekanism.common.registries.MekanismHeightProviderTypes.HEIGHT_PROVIDER_TYPES.registerAll(BuiltInRegistries.HEIGHT_PROVIDER_TYPE);
        mekanism.common.registries.MekanismPlacementModifiers.PLACEMENT_MODIFIERS.registerAll(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE);
        verifyWorldGenRegistration();
        mekanism.fabric.worldgen.FabricBiomeModifiers.initialize();
        mekanism.fabric.worldgen.FabricWorldGenConfig.initializeCallbacks();

        verifyGameEvent(MekanismGameEvents.SEISMIC_VIBRATION, "seismic_vibration", 64);
        verifyGameEvent(MekanismGameEvents.JETPACK_BURN, "jetpack_burn", 16);
        verifyGameEvent(MekanismGameEvents.GRAVITY_MODULATE, "gravity_modulate", 16);
        verifyGameEvent(MekanismGameEvents.GRAVITY_MODULATE_BOOSTED, "gravity_modulate_boosted", 32);
        LOGGER.info("MEKANISM_FABRIC_REGISTRY_PROOF key=mekanism:seismic_vibration canonical_identity=true entries=4");
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            //Revalidate persisted chunk-loader tickets exactly like the official loading-validation callback
            mekanism.common.tile.component.TileComponentChunkLoader.validateTickets(server);
            double ambient = HeatAPI.getAmbientTemp(server.overworld(), BlockPos.ZERO);
            if (!Double.isFinite(ambient) || ambient <= 0) {
                throw new IllegalStateException("Fabric positional ambient-temperature proof failed: " + ambient);
            }
            LOGGER.info("MEKANISM_FABRIC_HEAT_PROOF positional=true ambient_finite=true");
        });
    }

    private static long mekanismCount(net.minecraft.core.Registry<?> registry) {
        return registry.keySet().stream().filter(id -> id.getNamespace().equals(mekanism.api.MekanismAPI.MEKANISM_MODID)).count();
    }

    private static void verifyWorldGenRegistration() {
        verifyWorldGenEntry(BuiltInRegistries.FEATURE, mekanism.common.registries.MekanismFeatures.FEATURES.getEntries());
        verifyWorldGenEntry(BuiltInRegistries.INT_PROVIDER_TYPE, mekanism.common.registries.MekanismIntProviderTypes.INT_PROVIDER_TYPES.getEntries());
        verifyWorldGenEntry(BuiltInRegistries.HEIGHT_PROVIDER_TYPE, mekanism.common.registries.MekanismHeightProviderTypes.HEIGHT_PROVIDER_TYPES.getEntries());
        verifyWorldGenEntry(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, mekanism.common.registries.MekanismPlacementModifiers.PLACEMENT_MODIFIERS.getEntries());
        LOGGER.info("MEKANISM_FABRIC_WORLDGEN_REGISTRY_PROOF features=3 int_provider_types=2 height_provider_types=1 placement_modifier_types=1");
    }

    private static <T> void verifyWorldGenEntry(net.minecraft.core.Registry<T> registry,
          List<? extends mekanism.common.registration.MekanismDeferredHolder<T, ? extends T>> entries) {
        for (mekanism.common.registration.MekanismDeferredHolder<T, ? extends T> entry : entries) {
            T registered = registry.get(entry.getId());
            if (!entry.isBound() || registered != entry.value()) {
                throw new IllegalStateException("Mekanism worldgen registry entry was not registered: " + entry.getId());
            }
        }
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
              "rotary", "sawing", "mek_data", "clear_configuration", "bin_insert", "bin_extract")) {
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
