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
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        renderResolved(stack, context, matrices, vertexConsumers, light, overlay, model);
    }

    /** Render the same model already selected by ItemRenderer, without re-running its overrides. */
    public static void renderResolved(ItemStack stack, ItemDisplayContext context, com.mojang.blaze3d.vertex.PoseStack matrices,
          MultiBufferSource vertexConsumers, int light, int overlay, BakedModel model) {
        com.mojang.blaze3d.vertex.VertexConsumer buffer;
        if ((stack.is(ItemTags.COMPASSES) || stack.is(Items.CLOCK)) && stack.hasFoil()) {
            com.mojang.blaze3d.vertex.PoseStack.Pose pose = matrices.last().copy();
            if (context == ItemDisplayContext.GUI) {
                com.mojang.math.MatrixUtil.mulComponentWise(pose.pose(), 0.5F);
            } else if (context.firstPerson()) {
                com.mojang.math.MatrixUtil.mulComponentWise(pose.pose(), 0.75F);
            }
            buffer = ItemRenderer.getCompassFoilBuffer(vertexConsumers, RobitSpriteUploader.RENDER_TYPE, pose);
        } else {
            buffer = ItemRenderer.getFoilBufferDirect(vertexConsumers, RobitSpriteUploader.RENDER_TYPE, true, stack.hasFoil());
        }
        ResourceLocation skinTexture = skinTexture(stack);
        RandomSource random = RandomSource.create();
        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            renderQuads(stack, matrices, buffer, light, overlay, model.getQuads(null, direction, random), skinTexture);
        }
        random.setSeed(42L);
        renderQuads(stack, matrices, buffer, light, overlay, model.getQuads(null, null, random), skinTexture);
    }

    private static void renderQuads(ItemStack stack, com.mojang.blaze3d.vertex.PoseStack matrices,
          com.mojang.blaze3d.vertex.VertexConsumer buffer, int light, int overlay, List<BakedQuad> source, ResourceLocation skinTexture) {
        for (BakedQuad quad : RobitBakedModel.transformForSkin(source, skinTexture)) {
            int color = quad.isTinted() ? Minecraft.getInstance().itemColors.getColor(stack, quad.getTintIndex()) : -1;
            int packedLight = RobitBakedModel.lightFor(quad, light);
            buffer.putBulkData(matrices.last(), quad, new float[]{1, 1, 1, 1}, FastColor.ARGB32.red(color) / 255.0F,
                  FastColor.ARGB32.green(color) / 255.0F, FastColor.ARGB32.blue(color) / 255.0F, FastColor.ARGB32.alpha(color) / 255.0F,
                  new int[]{packedLight, packedLight, packedLight, packedLight}, overlay, true);
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
