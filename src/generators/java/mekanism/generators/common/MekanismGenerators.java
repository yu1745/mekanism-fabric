package mekanism.generators.common;

import mekanism.api.MekanismIMC;
import mekanism.api.MekanismAPI;
import mekanism.common.base.ModModuleState;
import mekanism.common.base.IModModule;
import mekanism.common.command.builders.BuildCommand;
import mekanism.common.lib.Version;
import mekanism.common.lib.multiblock.MultiblockManager;
import mekanism.generators.common.config.MekanismGeneratorsConfig;
import mekanism.generators.common.content.fission.FissionReactorCache;
import mekanism.generators.common.content.fission.FissionReactorMultiblockData;
import mekanism.generators.common.content.fission.FissionReactorValidator;
import mekanism.generators.common.content.fusion.FusionReactorCache;
import mekanism.generators.common.content.fusion.FusionReactorMultiblockData;
import mekanism.generators.common.content.fusion.FusionReactorValidator;
import mekanism.generators.common.content.turbine.TurbineCache;
import mekanism.generators.common.content.turbine.TurbineMultiblockData;
import mekanism.generators.common.content.turbine.TurbineValidator;
import mekanism.generators.common.network.GeneratorsPacketHandler;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.registries.GeneratorsBuilders.FissionReactorBuilder;
import mekanism.generators.common.registries.GeneratorsBuilders.FusionReactorBuilder;
import mekanism.generators.common.registries.GeneratorsBuilders.TurbineBuilder;
import mekanism.generators.common.registries.GeneratorsChemicals;
import mekanism.generators.common.registries.GeneratorsContainerTypes;
import mekanism.generators.common.registries.GeneratorsCreativeTabs;
import mekanism.generators.common.registries.GeneratorsDataComponents;
import mekanism.generators.common.registries.GeneratorsFluids;
import mekanism.generators.common.registries.GeneratorsItems;
import mekanism.generators.common.registries.GeneratorsModules;
import mekanism.generators.common.registries.GeneratorsSounds;
import mekanism.generators.common.registries.GeneratorsTileEntityTypes;
import net.minecraft.resources.ResourceLocation;

public class MekanismGenerators implements IModModule, net.fabricmc.api.ModInitializer {

    public static final String MODID = "mekanismgenerators";

    public static MekanismGenerators instance;

    /**
     * MekanismGenerators version number
     */
    public Version versionNumber;
    /**
     * Mekanism Generators Packet Pipeline
     */
    private GeneratorsPacketHandler packetHandler;

    public static final MultiblockManager<TurbineMultiblockData> turbineManager = new MultiblockManager<>("industrialTurbine", TurbineCache::new, TurbineValidator::new);
    public static final MultiblockManager<FissionReactorMultiblockData> fissionReactorManager = new MultiblockManager<>("fissionReactor", FissionReactorCache::new, FissionReactorValidator::new);
    public static final MultiblockManager<FusionReactorMultiblockData> fusionReactorManager = new MultiblockManager<>("fusionReactor", FusionReactorCache::new, FusionReactorValidator::new);

    @Override
    public void onInitialize() {
        instance = this;
        ModModuleState.addModule(this);
        String version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MODID).orElseThrow()
              .getMetadata().getVersion().getFriendlyString().replace("-fabric-bootstrap", "");
        versionNumber = new Version(version);
        MekanismGeneratorsConfig.registerConfigs();
        GeneratorsDataComponents.DATA_COMPONENTS.registerAll(net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE);
        GeneratorsBlocks.BLOCKS.registerAll(net.minecraft.core.registries.BuiltInRegistries.BLOCK, net.minecraft.core.registries.BuiltInRegistries.ITEM);
        GeneratorsItems.ITEMS.registerAll(net.minecraft.core.registries.BuiltInRegistries.ITEM);
        GeneratorsFluids.FLUIDS.registerAll(net.minecraft.core.registries.BuiltInRegistries.FLUID,
              net.minecraft.core.registries.BuiltInRegistries.BLOCK, net.minecraft.core.registries.BuiltInRegistries.ITEM);
        GeneratorsSounds.SOUND_EVENTS.registerAll(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT);
        GeneratorsContainerTypes.CONTAINER_TYPES.registerAll(net.minecraft.core.registries.BuiltInRegistries.MENU);
        GeneratorsTileEntityTypes.TILE_ENTITY_TYPES.registerAll(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE);
        GeneratorsCreativeTabs.CREATIVE_TABS.registerAll(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB);
        GeneratorsCreativeTabs.registerVanillaTabs();
        packetHandler = new GeneratorsPacketHandler();
        commonSetup();
    }

    public static GeneratorsPacketHandler packetHandler() {
        return instance.packetHandler;
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup() {
            //Register dispenser behaviors
            GeneratorsFluids.FLUIDS.registerBucketDispenserBehavior();
            //Register extended build commands (in enqueue as it is not thread safe)
            BuildCommand.register("turbine", GeneratorsLang.TURBINE, new TurbineBuilder());
            BuildCommand.register("fission", GeneratorsLang.FISSION_REACTOR, new FissionReactorBuilder());
            BuildCommand.register("fusion", GeneratorsLang.FUSION_REACTOR, new FusionReactorBuilder());

        //Finalization
        MekanismAPI.logger.info("Loaded 'Mekanism: Generators' module.");
    }

    @Override
    public Version getVersion() {
        return versionNumber;
    }

    @Override
    public String getName() {
        return "Generators";
    }

    @Override
    public void resetClientDimensionChanged() {
        TurbineMultiblockData.clientRotationMap.clear();
    }
}
