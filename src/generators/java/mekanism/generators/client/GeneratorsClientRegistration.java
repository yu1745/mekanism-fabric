package mekanism.generators.client;

import mekanism.api.gear.IModuleHelper;
import mekanism.client.model.baked.ExtensionBakedModel.TransformedBakedModel;
import mekanism.client.render.lib.QuadTransformation;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.generators.client.gui.GuiBioGenerator;
import mekanism.generators.client.gui.GuiFissionReactor;
import mekanism.generators.client.gui.GuiFissionReactorLogicAdapter;
import mekanism.generators.client.gui.GuiFissionReactorStats;
import mekanism.generators.client.gui.GuiFusionReactorController;
import mekanism.generators.client.gui.GuiFusionReactorFuel;
import mekanism.generators.client.gui.GuiFusionReactorHeat;
import mekanism.generators.client.gui.GuiFusionReactorLogicAdapter;
import mekanism.generators.client.gui.GuiFusionReactorStats;
import mekanism.generators.client.gui.GuiGasGenerator;
import mekanism.generators.client.gui.GuiHeatGenerator;
import mekanism.generators.client.gui.GuiIndustrialTurbine;
import mekanism.generators.client.gui.GuiSolarGenerator;
import mekanism.generators.client.gui.GuiTurbineStats;
import mekanism.generators.client.gui.GuiWindGenerator;
import mekanism.generators.client.model.ModelTurbine;
import mekanism.generators.client.model.ModelWindGenerator;
import mekanism.generators.client.render.RenderBioGenerator;
import mekanism.generators.client.render.RenderFissionReactor;
import mekanism.generators.client.render.RenderFusionReactor;
import mekanism.generators.client.render.RenderIndustrialTurbine;
import mekanism.generators.client.render.RenderTurbineRotor;
import mekanism.generators.client.render.RenderWindGenerator;
import mekanism.generators.client.render.item.RenderWindGeneratorItem;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.registries.GeneratorsContainerTypes;
import mekanism.generators.common.registries.GeneratorsFluids;
import mekanism.generators.common.registries.GeneratorsModules;
import mekanism.generators.common.registries.GeneratorsTileEntityTypes;
import mekanism.generators.common.tile.TileEntityAdvancedSolarGenerator;
import mekanism.generators.common.tile.TileEntitySolarGenerator;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;

public class GeneratorsClientRegistration {

    private GeneratorsClientRegistration() {
    }

    public static void init() {

            //Set fluids to a translucent render layer
            for (Holder<Fluid> fluid : GeneratorsFluids.FLUIDS.getFluidEntries()) {
                net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(), fluid.value());
            }

        // adv solar gen requires to be translated up 1 block, so handle the model separately
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context ->
              context.modifyModelAfterBake().register((model, bake) -> {
                  if (MekanismGenerators.rl("block/advanced_solar_generator").equals(bake.resourceId())) {
                      return new TransformedBakedModel<Void>(model, QuadTransformation.translate(0, 1, 0));
                  }
                  return model;
              }));
        //TODO: Eventually make use of these custom model wrappers
        //ClientRegistration.addCustomModel(GeneratorsBlocks.FISSION_FUEL_ASSEMBLY, (orig, evt) -> new FuelAssemblyBakedModel(orig, 0.75));
        //ClientRegistration.addCustomModel(GeneratorsBlocks.CONTROL_ROD_ASSEMBLY, (orig, evt) -> new FuelAssemblyBakedModel(orig, 0.375));

        IModuleHelper moduleHelper = IModuleHelper.INSTANCE;
        moduleHelper.addMekaSuitModuleModels(MekanismGenerators.rl("models/entity/mekasuit_modules.obj"));
        moduleHelper.addMekaSuitModuleModelSpec("solar_helmet", GeneratorsModules.SOLAR_RECHARGING_UNIT, EquipmentSlot.HEAD);
    }

    public static void registerRenderers() {
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(GeneratorsTileEntityTypes.BIO_GENERATOR.get(), RenderBioGenerator::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(GeneratorsTileEntityTypes.FUSION_REACTOR_CONTROLLER.get(), RenderFusionReactor::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(GeneratorsTileEntityTypes.FISSION_REACTOR_CASING.get(), RenderFissionReactor::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(GeneratorsTileEntityTypes.FISSION_REACTOR_PORT.get(), RenderFissionReactor::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(GeneratorsTileEntityTypes.FISSION_REACTOR_LOGIC_ADAPTER.get(), RenderFissionReactor::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(GeneratorsTileEntityTypes.TURBINE_CASING.get(), RenderIndustrialTurbine::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(GeneratorsTileEntityTypes.TURBINE_ROTOR.get(), RenderTurbineRotor::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(GeneratorsTileEntityTypes.TURBINE_VALVE.get(), RenderIndustrialTurbine::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(GeneratorsTileEntityTypes.TURBINE_VENT.get(), RenderIndustrialTurbine::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(GeneratorsTileEntityTypes.WIND_GENERATOR.get(), RenderWindGenerator::new);
    }

    public static void registerLayer() {
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(ModelWindGenerator.GENERATOR_LAYER, ModelWindGenerator::createLayerDefinition);
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(ModelTurbine.TURBINE_LAYER, ModelTurbine::createLayerDefinition);
    }

    public static void registerClientReloadListeners() {
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
              .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                  @Override
                  public net.minecraft.resources.ResourceLocation getFabricId() {
                      return MekanismGenerators.rl("client_resources");
                  }

                  @Override
                  public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager resources) {
                      RenderWindGeneratorItem.RENDERER.onResourceManagerReload(resources);
                      RenderBioGenerator.resetCachedModels();
                      RenderFissionReactor.resetCachedModels();
                      GeneratorsSpecialColors.GUI_OBJECTS.parse(MekanismGenerators.rl("textures/colormap/gui_objects.png"));
                  }
              });
    }

    @SuppressWarnings("Convert2MethodRef")
    public static void registerScreens() {
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.BIO_GENERATOR.get(), GuiBioGenerator::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.GAS_BURNING_GENERATOR.get(), GuiGasGenerator::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.HEAT_GENERATOR.get(), GuiHeatGenerator::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.INDUSTRIAL_TURBINE.get(), GuiIndustrialTurbine::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.FISSION_REACTOR.get(), GuiFissionReactor::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.FISSION_REACTOR_STATS.get(), GuiFissionReactorStats::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.FISSION_REACTOR_LOGIC_ADAPTER.get(), GuiFissionReactorLogicAdapter::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.FUSION_REACTOR_CONTROLLER.get(), GuiFusionReactorController::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.FUSION_REACTOR_FUEL.get(), GuiFusionReactorFuel::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.FUSION_REACTOR_HEAT.get(), GuiFusionReactorHeat::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.FUSION_REACTOR_LOGIC_ADAPTER.get(), GuiFusionReactorLogicAdapter::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.FUSION_REACTOR_STATS.get(), GuiFusionReactorStats::new);
        // for some reason java is unable to infer the types with this generics structure, so we include constructor signature ourselves
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.SOLAR_GENERATOR.get(), (MekanismTileContainer<TileEntitySolarGenerator> container, Inventory inv, Component title) -> new GuiSolarGenerator<>(container, inv, title));
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.ADVANCED_SOLAR_GENERATOR.get(), (MekanismTileContainer<TileEntityAdvancedSolarGenerator> container, Inventory inv, Component title) -> new GuiSolarGenerator<>(container, inv, title));
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.TURBINE_STATS.get(), GuiTurbineStats::new);
        net.minecraft.client.gui.screens.MenuScreens.register(GeneratorsContainerTypes.WIND_GENERATOR.get(), GuiWindGenerator::new);
    }

    public static void registerItemColorHandlers() {
        mekanism.fabric.client.render.item.FabricFluidBucketRegistration.register(GeneratorsFluids.FLUIDS);
    }

    public static void registerClientExtensions() {
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(GeneratorsBlocks.WIND_GENERATOR.asItem(),
              (stack, context, matrices, consumers, light, overlay) -> RenderWindGeneratorItem.RENDERER.renderByItem(stack, context, matrices, consumers, light, overlay));

        mekanism.fabric.client.FabricFluidColors.register(GeneratorsFluids.FLUIDS);
        net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE
              .setBlockTransparency(GeneratorsBlocks.LASER_FOCUS_MATRIX.get(), true);
    }
}