package mekanism.additions.client;

import java.util.HashMap;
import mekanism.additions.client.model.AdditionsModelCache;
import mekanism.additions.client.model.ModelBabyCreeper;
import mekanism.additions.client.render.entity.RenderBabyCreeper;
import mekanism.additions.client.render.entity.RenderBabyEnderman;
import mekanism.additions.client.render.entity.RenderBalloon;
import mekanism.additions.common.MekanismAdditions;
import mekanism.additions.common.item.ItemBalloon;
import mekanism.additions.common.item.ItemWalkieTalkie.WalkieData;
import mekanism.additions.common.registries.AdditionsBlocks;
import mekanism.additions.common.registries.AdditionsDataComponents;
import mekanism.additions.common.registries.AdditionsEntityTypes;
import mekanism.additions.common.registries.AdditionsItems;
import mekanism.common.block.interfaces.IColoredBlock;
import mekanism.fabric.client.GearInteractionPolicy;
import mekanism.fabric.client.model.FabricModelCachePlugin;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.BoggedRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.StrayRenderer;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.WitherSkeletonRenderer;
import net.minecraft.client.renderer.item.ItemProperties;

public class AdditionsClientRegistration {
    private AdditionsClientRegistration() {
    }

    public static void initialize() {
        ItemProperties.PROPERTIES.computeIfAbsent(AdditionsItems.WALKIE_TALKIE.asItem(), ignored -> new HashMap<>())
              .put(MekanismAdditions.rl("channel"), (stack, world, entity, seed) -> {
                  WalkieData data = stack.getOrDefault(AdditionsDataComponents.WALKIE_DATA.get(), WalkieData.DEFAULT);
                  return data.running() ? data.channel() : 0;
              });
        GearInteractionPolicy.registerComponentInsensitiveItem(AdditionsItems.WALKIE_TALKIE.asItem());
        AdditionsKeyHandler.registerKeybindings();
        EntityRendererRegistry.register(AdditionsEntityTypes.OBSIDIAN_TNT.get(), TntRenderer::new);
        EntityRendererRegistry.register(AdditionsEntityTypes.BALLOON.get(), RenderBalloon::new);
        EntityRendererRegistry.register(AdditionsEntityTypes.BABY_BOGGED.get(), BoggedRenderer::new);
        EntityRendererRegistry.register(AdditionsEntityTypes.BABY_CREEPER.get(), RenderBabyCreeper::new);
        EntityRendererRegistry.register(AdditionsEntityTypes.BABY_ENDERMAN.get(), RenderBabyEnderman::new);
        EntityRendererRegistry.register(AdditionsEntityTypes.BABY_SKELETON.get(), SkeletonRenderer::new);
        EntityRendererRegistry.register(AdditionsEntityTypes.BABY_STRAY.get(), StrayRenderer::new);
        EntityRendererRegistry.register(AdditionsEntityTypes.BABY_WITHER_SKELETON.get(), WitherSkeletonRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(ModelBabyCreeper.CREEPER_LAYER, () -> ModelBabyCreeper.createBodyLayer(CubeDeformation.NONE));
        EntityModelLayerRegistry.registerModelLayer(ModelBabyCreeper.ARMOR_LAYER, () -> ModelBabyCreeper.createBodyLayer(new CubeDeformation(1)));
        FabricModelCachePlugin.registerModuleCache(AdditionsModelCache.INSTANCE);
        for (var holder : AdditionsBlocks.BLOCKS.getPrimaryEntries()) {
            var block = holder.get();
            if (block instanceof IColoredBlock colored) {
                ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> colored.getColor().getPackedColor(), block);
                ColorProviderRegistry.ITEM.register((stack, tint) -> colored.getColor().getPackedColor(), block.asItem());
            }
        }
        for (var holder : AdditionsItems.BALLOONS.values()) {
            ColorProviderRegistry.ITEM.register((stack, tint) -> ((ItemBalloon) stack.getItem()).getColor().getPackedColor(), holder.asItem());
        }
        for (var group : java.util.List.of(AdditionsBlocks.TRANSPARENT_PLASTIC_BLOCKS, AdditionsBlocks.TRANSPARENT_PLASTIC_STAIRS,
              AdditionsBlocks.TRANSPARENT_PLASTIC_SLABS)) {
            for (var block : group.values()) BlockRenderLayerMap.INSTANCE.putBlock(block.get(), RenderType.translucent());
        }
    }
}
