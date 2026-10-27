package mekanism.client.render.transmitter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.client.model.MekanismModelCache;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.lib.Quad;
import mekanism.client.render.lib.QuadUtils;
import mekanism.client.render.tileentity.MekanismTileEntityRenderer;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

@NothingNullByDefault
public abstract class RenderTransmitterBase<TRANSMITTER extends TileEntityTransmitter> extends MekanismTileEntityRenderer<TRANSMITTER> {

    public static final ResourceLocation MODEL_LOCATION = MekanismUtils.getResource(ResourceType.MODEL, "transmitter_contents.obj");
    //Fabric port: the official bake rode NeoForge's ModelBaker internals with a StandaloneGeometryBakingContext
    //(gui3d/block light/ao all off); the port's OBJModelData re-bakes per visible-parts set with the icon override,
    //and the flat normals below carry the no-diffuse rule.
    private static final mekanism.client.model.BaseModelCache.OBJModelData CONTENTS = new mekanism.client.model.BaseModelCache.OBJModelData(MODEL_LOCATION);
    private static final Vector3f NORMAL = new Vector3f(1, 1, 1).normalize();

    public static void onStitch() {
        CONTENTS.clearCached();
    }

    private static List<BakedQuad> getBakedQuads(List<String> visible, TextureAtlasSprite icon, Level world) {
        List<BakedQuad> bakedQuads = CONTENTS.getQuads(new java.util.HashSet<>(visible), world.getRandom(), icon);
        List<Quad> unpackedQuads = QuadUtils.unpack(bakedQuads);
        for (Quad unpackedQuad : unpackedQuads) {
            //Set the normals to ones that ignore the diffuse light in the same way we do it in Render Resizable Cuboid
            unpackedQuad.vertexTransform(vertex -> vertex.normal(NORMAL));
        }
        return QuadUtils.bake(unpackedQuads);
    }

    protected RenderTransmitterBase(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    protected void renderModel(TRANSMITTER transmitter, PoseStack matrix, VertexConsumer builder, int rgb, float alpha, int light, int overlayLight,
          TextureAtlasSprite icon) {
        List<String> list = new ArrayList<>(EnumUtils.DIRECTIONS.length);
        for (Direction side : EnumUtils.DIRECTIONS) {
            list.add(side.getSerializedName() + transmitter.getTransmitter().getConnectionType(side).name());
        }
        renderModel(transmitter, matrix, builder, MekanismRenderer.getRed(rgb), MekanismRenderer.getGreen(rgb), MekanismRenderer.getBlue(rgb), alpha, light,
              overlayLight, icon, list);
    }

    protected void renderModel(TRANSMITTER transmitter, PoseStack matrix, VertexConsumer builder, float red, float green, float blue, float alpha, int light,
          int overlayLight, TextureAtlasSprite icon, List<String> visible) {
        if (!visible.isEmpty()) {
            Pose entry = matrix.last();
            //Get all the sides
            for (BakedQuad quad : getBakedQuads(visible, icon, transmitter.getLevel())) {
                builder.putBulkData(entry, quad, red, green, blue, alpha, light, overlayLight);
            }
        }
    }

    @Override
    public boolean shouldRender(TRANSMITTER tile, Vec3 camera) {
        return shouldRenderTransmitter(tile, camera) && super.shouldRender(tile, camera);
    }

    protected boolean shouldRenderTransmitter(TRANSMITTER tile, Vec3 camera) {
        return !MekanismConfig.client.opaqueTransmitters.get();
    }

    private record ContentsModelData(List<String> visible, TextureAtlasSprite icon) {
    }
}