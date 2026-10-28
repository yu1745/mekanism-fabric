package mekanism.fabric.client.render.item;

import java.util.List;
import mekanism.api.MekanismAPI;
import mekanism.client.RobitSpriteUploader;
import mekanism.client.model.robit.RobitBakedModel;
import mekanism.common.item.ItemRobit;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.registries.MekanismRobitSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric builtin renderer for the robit item: the shipped models bake with missingno placeholder textures, and the
 * draw applies the official per-skin transform (missingno replaced by the selected skin's texture) on the robit atlas
 * - vanilla has no render-type hook for item models, so the builtin path replaces the official getRenderTypes half.
 */
public final class FabricRobitItemRenderer {

    private FabricRobitItemRenderer() {
    }

    public static void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context, @NotNull com.mojang.blaze3d.vertex.PoseStack matrices,
          @NotNull MultiBufferSource vertexConsumers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = resolveModel(stack, minecraft.level, null, 0, mekanism.client.model.MekanismModelCache.INSTANCE.getBaseRobit());
        ResourceLocation skinTexture = skinTexture(stack);
        com.mojang.blaze3d.vertex.PoseStack poseStack = matrices;
        List<BakedQuad> quads = RobitBakedModel.transformForSkin(model.getQuads(null, null, Minecraft.getInstance().level != null
              ? Minecraft.getInstance().level.random : net.minecraft.util.RandomSource.create()), skinTexture);
        com.mojang.blaze3d.vertex.VertexConsumer buffer = vertexConsumers.getBuffer(RobitSpriteUploader.RENDER_TYPE);
        com.mojang.blaze3d.vertex.PoseStack.Pose pose = poseStack.last();
        for (BakedQuad quad : quads) {
            buffer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, RobitBakedModel.lightFor(quad, light), overlay);
        }
    }

    @NotNull
    static BakedModel resolveModel(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.client.multiplayer.ClientLevel level,
          @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity entity, int seed, @org.jetbrains.annotations.Nullable BakedModel fallback) {
        var world = level != null ? level : entity != null ? entity.level() : Minecraft.getInstance().level;
        BakedModel selected = fallback;
        if (world != null && stack.getItem() instanceof ItemRobit) {
            var key = stack.getOrDefault(MekanismDataComponents.ROBIT_SKIN.get(), MekanismRobitSkins.BASE);
            BakedModel skinModel = mekanism.client.model.MekanismModelCache.INSTANCE.getRobitSkin(MekanismRobitSkins.lookup(world.registryAccess(), key));
            if (skinModel != null) {
                selected = skinModel;
            }
        }
        if (selected == null) {
            selected = Minecraft.getInstance().getModelManager().getMissingModel();
        }
        BakedModel override = selected.getOverrides().resolve(selected, stack, level, entity, seed);
        return override == null ? selected : override;
    }

    @NotNull
    private static ResourceLocation skinTexture(@NotNull ItemStack stack) {
        if (Minecraft.getInstance().level == null) {
            return ResourceLocation.fromNamespaceAndPath(MekanismAPI.MEKANISM_MODID, "robit");
        }
        var skinKey = stack.getOrDefault(MekanismDataComponents.ROBIT_SKIN.get(), MekanismRobitSkins.BASE);
        var skinLookup = MekanismRobitSkins.lookup(Minecraft.getInstance().level.registryAccess(), skinKey);
        List<ResourceLocation> textures = skinLookup.skin().textures();
        return textures.isEmpty() ? ResourceLocation.fromNamespaceAndPath(MekanismAPI.MEKANISM_MODID, "robit") : textures.getFirst();
    }
}
