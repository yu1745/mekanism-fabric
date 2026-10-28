package mekanism.fabric.client.render.item;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismItems;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Wires the two block-item ISBERs (energy cube, fluid tank) the way NeoForge's {@code registerItemExtensions} does:
 * the item models stay the official JSONs, but their baked quads are suppressed so rendering fully delegates to the
 * registered builtin renderers (the Fabric registry otherwise double-renders a quad-bearing model). The suppressor
 * delegates to the officially baked model, so parent-resolved display transforms, particles and overrides are
 * preserved exactly. The three official item bar decorators have no Fabric 1.21.1 surface and stay deferred.
 */
public final class FabricItemRendererRegistration implements ModelLoadingPlugin {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Mekanism/FabricItemRenderers");

    private static Set<ResourceLocation> suppressedItemModels() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (var block : new mekanism.common.registration.impl.BlockRegistryObject<?, ?>[]{MekanismBlocks.BASIC_ENERGY_CUBE, MekanismBlocks.ADVANCED_ENERGY_CUBE,
              MekanismBlocks.ELITE_ENERGY_CUBE, MekanismBlocks.ULTIMATE_ENERGY_CUBE, MekanismBlocks.CREATIVE_ENERGY_CUBE,
              MekanismBlocks.BASIC_FLUID_TANK, MekanismBlocks.ADVANCED_FLUID_TANK, MekanismBlocks.ELITE_FLUID_TANK,
              MekanismBlocks.ULTIMATE_FLUID_TANK, MekanismBlocks.CREATIVE_FLUID_TANK}) {
            ids.add(ResourceLocation.fromNamespaceAndPath("mekanism", "item/" + block.getId().getPath()));
        }
        //Robit's inventory variant is wrapped after bake; its resource models must remain available as geometry.
        return ids;
    }

    /** Registers the shared ISBER singletons, and rebuilds their model layers after vanilla's entity model set. */
    public static void registerRenderers() {
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
              .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                  @Override
                  public ResourceLocation getFabricId() {
                      return ResourceLocation.fromNamespaceAndPath("mekanism", "item_renderers");
                  }

                  @Override
                  public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager resources) {
                      int reloaded = 0;
                      for (var itemRenderer : List.<net.minecraft.server.packs.resources.ResourceManagerReloadListener>of(
                            mekanism.client.render.armor.JetpackArmor.JETPACK,
                            mekanism.client.render.armor.JetpackArmor.ARMORED_JETPACK,
                            mekanism.client.render.armor.FreeRunnerArmor.FREE_RUNNERS,
                            mekanism.client.render.armor.FreeRunnerArmor.ARMORED_FREE_RUNNERS,
                            mekanism.client.render.armor.ScubaMaskArmor.SCUBA_MASK,
                            mekanism.client.render.armor.ScubaTankArmor.SCUBA_TANK,
                            mekanism.client.render.item.block.RenderEnergyCubeItem.RENDERER,
                            mekanism.client.render.item.gear.RenderJetpack.RENDERER,
                            mekanism.client.render.item.gear.RenderJetpack.ARMORED_RENDERER,
                            mekanism.client.render.item.gear.RenderFreeRunners.RENDERER,
                            mekanism.client.render.item.gear.RenderFreeRunners.ARMORED_RENDERER,
                            mekanism.client.render.item.gear.RenderScubaMask.RENDERER,
                            mekanism.client.render.item.gear.RenderScubaTank.RENDERER,
                            mekanism.client.render.item.gear.RenderAtomicDisassembler.RENDERER,
                            mekanism.client.render.item.gear.RenderFlameThrower.RENDERER)) {
                          itemRenderer.onResourceManagerReload(resources);
                          reloaded++;
                      }
                      LOGGER.info("MEKANISM_FABRIC_GEAR_MODEL_RELOAD_PROOF listeners={}", reloaded);
                  }
              });
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismBlocks.BASIC_ENERGY_CUBE.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.block.RenderEnergyCubeItem.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismBlocks.ADVANCED_ENERGY_CUBE.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.block.RenderEnergyCubeItem.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismBlocks.ELITE_ENERGY_CUBE.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.block.RenderEnergyCubeItem.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismBlocks.ULTIMATE_ENERGY_CUBE.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.block.RenderEnergyCubeItem.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismBlocks.CREATIVE_ENERGY_CUBE.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.block.RenderEnergyCubeItem.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismBlocks.BASIC_FLUID_TANK.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.block.RenderFluidTankItem.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismBlocks.ADVANCED_FLUID_TANK.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.block.RenderFluidTankItem.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismBlocks.ELITE_FLUID_TANK.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.block.RenderFluidTankItem.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismBlocks.ULTIMATE_FLUID_TANK.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.block.RenderFluidTankItem.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismBlocks.CREATIVE_FLUID_TANK.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.block.RenderFluidTankItem.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        //Gear items: the official item JSONs already parent onto minecraft:builtin/entity, so the vanilla builtin
        //model produces no quads and no suppression is needed - only the renderer wiring (the official
        //registerItem(MekRenderProperties/MekCustomArmorRenderProperties) item half; the armor-layer half stays
        //deferred with the armor lane).
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismItems.JETPACK.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.gear.RenderJetpack.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismItems.ARMORED_JETPACK.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.gear.RenderJetpack.ARMORED_RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismItems.FREE_RUNNERS.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.gear.RenderFreeRunners.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismItems.ARMORED_FREE_RUNNERS.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.gear.RenderFreeRunners.ARMORED_RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismItems.SCUBA_MASK.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.gear.RenderScubaMask.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismItems.SCUBA_TANK.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.gear.RenderScubaTank.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismItems.ATOMIC_DISASSEMBLER.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.gear.RenderAtomicDisassembler.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismItems.FLAMETHROWER.asItem(), (stack, context, matrices, vertexConsumers, light, overlay) ->
              mekanism.client.render.item.gear.RenderFlameThrower.RENDERER.renderByItem(stack, context, matrices, vertexConsumers, light, overlay));
        //Robit items: the per-skin quads draw on the robit atlas.
        BuiltinItemRendererRegistry.INSTANCE.register(MekanismItems.ROBIT.asItem(), FabricRobitItemRenderer::renderByItem);
    }

    @Override
    public void onInitializeModelLoader(Context context) {
        Set<ResourceLocation> suppressed = suppressedItemModels();
        //The suppression wraps the model at LOAD time: the loaded unbaked (the vanilla-parsed item json, whose
        //parents resolve through the model plugin's prepared models) is returned inside QuadsEmptyModel, which
        //bakes the official model but emits empty quads. The original resolveModel self-reference
        //(ctx.getOrLoadModel(ctx.id())) stopped being tolerated by the model-loading api's recursion guard, and a
        //post-bake wrap breaks the item models that parent onto geometry-carrier blocks (vanilla would run its
        //item generator over the carrier's animated textures).
        context.modifyModelOnLoad().register((model, loadContext) -> {
            net.minecraft.resources.ResourceLocation resourceId = loadContext.resourceId();
            if (resourceId != null && suppressed.contains(resourceId) && model instanceof net.minecraft.client.renderer.block.model.BlockModel blockModel) {
                return new QuadsEmptyModel(blockModel);
            }
            return model;
        });
        // Robit's geometry uses a dedicated UnbakedModel rather than BlockModel, so the load-time suppressor above
        // cannot wrap it. Wrap only the inventory variant, leaving the resource model available to the entity renderer.
        context.modifyModelAfterBake().register(net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier.WRAP_PHASE, (model, bake) -> {
            if (model != null && net.minecraft.client.resources.model.ModelResourceLocation.inventory(MekanismItems.ROBIT.getId()).equals(bake.topLevelId())) {
                LOGGER.info("MEKANISM_FABRIC_ROBIT_ITEM_MODEL_PROOF builtin=true");
                return new RobitItemBakedModel(model);
            }
            return model;
        });
        LOGGER.info("MEKANISM_FABRIC_ITEM_RENDERER_PROOF suppressed_models={} builtin_renderers=19", suppressed.size());
    }

    /** Keeps the official unbaked model for dependencies/parents; the bake only suppresses the quads. */
    private record QuadsEmptyModel(net.minecraft.client.renderer.block.model.BlockModel official) implements UnbakedModel {

        @Override
        public Collection<ResourceLocation> getDependencies() {
            return official.getDependencies();
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
            official.resolveParents(resolver);
        }

        @Override
        public BakedModel bake(@Nullable ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
            BakedModel officialBaked = official.bake(baker, spriteGetter, state);
            return new EmptyQuadsBakedModel(officialBaked);
        }
    }

    private record RobitItemBakedModel(BakedModel official) implements BakedModel {
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) { return List.of(); }
        @Override public boolean useAmbientOcclusion() { return official.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return official.isGui3d(); }
        @Override public boolean usesBlockLight() { return official.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return true; }
        @Override public TextureAtlasSprite getParticleIcon() { return official.getParticleIcon(); }
        @Override public ItemTransforms getTransforms() { return official.getTransforms(); }
        @Override public ItemOverrides getOverrides() {
            return new ItemOverrides(null, null, List.of()) {
                @Override
                public BakedModel resolve(BakedModel model, net.minecraft.world.item.ItemStack stack,
                      @Nullable net.minecraft.client.multiplayer.ClientLevel level, @Nullable net.minecraft.world.entity.LivingEntity entity, int seed) {
                    BakedModel resolved = FabricRobitItemRenderer.resolveModel(stack, level, entity, seed, official);
                    return new EmptyQuadsBakedModel(resolved);
                }
            };
        }
    }

    private record EmptyQuadsBakedModel(BakedModel official) implements BakedModel {

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
            return List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return official.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return official.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return official.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            //The builtin renderer takes over, matching NeoForge's custom-renderer delegation.
            return true;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return official.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return official.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return official.getOverrides();
        }
    }
}
