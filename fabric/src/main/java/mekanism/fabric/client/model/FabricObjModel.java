package mekanism.fabric.client.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import mekanism.fabric.client.render.obj.TransmitterObjGeometry;
import mekanism.fabric.client.render.obj.TransmitterQuadBaker;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * {@code neoforge:obj} semantics on the shipped Meka-Tool entity OBJs: geometry is parsed off-thread (including the
 * {@code mtllib}/{@code map_Kd} texture bindings), every group is baked through the shared OBJ quad baker, and the
 * display transforms plus gui-light/ambient-occlusion defaults come from the declaring JSON's real parent chain.
 * A perspective's parent reuses these OBJ quads with the child's resolved properties.
 *
 * <p>Extends BlockModel for the same vanilla parent-resolution requirement documented on the composite model.</p>
 */
public final class FabricObjModel extends BlockModel {

    private final ResourceLocation id;
    private final TransmitterObjGeometry geometry;

    private FabricObjModel(ResourceLocation id, String propertyJson, TransmitterObjGeometry geometry) {
        super(FabricModelJson.parentId(propertyJson), List.<BlockElement>of(), FabricModelJson.textures(propertyJson),
              FabricModelJson.ambientOcclusion(propertyJson), FabricModelJson.guiLight(propertyJson),
              FabricModelJson.transforms(propertyJson), FabricModelJson.overrides(propertyJson));
        this.id = id;
        this.geometry = geometry;
        this.name = id.toString();
    }

    /**
     * Builds the model from its {@code neoforge:obj} definition and the JSON whose properties are authoritative: the
     * definition itself when standalone, the {@code separate_transforms} outer model when the obj is a base.
     */
    static FabricObjModel of(ResourceLocation id, String propertyJson, TransmitterObjGeometry geometry) {
        return new FabricObjModel(id, propertyJson, geometry);
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        //Sprites resolve directly through the bake sprite getter; only the real property parent chain is scheduled.
        return super.getDependencies();
    }

    @Override
    public BlockModel getRootModel() {
        //The generated-item shortcut must not replace custom OBJ geometry with an empty flat item.
        return this;
    }

    @Override
    public BakedModel bake(@Nullable ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        return bakeInherited(this, baker, spriteGetter, state);
    }

    public BakedModel bakeInherited(BlockModel properties, @Nullable ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        Function<String, TextureAtlasSprite> sprites = alias -> spriteGetter.apply(new Material(
              TextureAtlas.LOCATION_BLOCKS, ResourceLocation.parse(alias)));
        TransmitterQuadBaker quadBaker = new TransmitterQuadBaker(geometry, sprites, state);
        List<BakedQuad> quads = new ArrayList<>();
        for (String group : geometry.groups().keySet()) {
            quads.addAll(quadBaker.bakeGroup(group, null));
        }
        //The particle fallback follows the first declared material; the shipped OBJs always declare one.
        String firstAlias = geometry.groups().values().iterator().next().iterator().next().material();
        return new FabricMergedQuadsModel(id, quads, sprites.apply(firstAlias), properties.getTransforms(),
              properties.hasAmbientOcclusion(), true, properties.getGuiLight() == BlockModel.GuiLight.SIDE);
    }
}
