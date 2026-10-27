package mekanism.fabric.client.model;

import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import mekanism.common.registration.impl.FluidDeferredRegister.MekanismFlowingFluid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Fabric port of the official chemical bucket geometry. Vanilla resolves the supplied NeoForge bucket parent for
 * texture and display properties; this model bakes the base sheath and the mask-shaped, tinted fluid layer. The mask
 * scan is adapted from NeoForge's LGPL-2.1-only UnbakedGeometryHelper; see META-INF/licenses/NeoForge-NOTICE.txt.
 */
public final class FabricFluidContainerModel implements UnbakedModel {

    //NeoForge composes this scale with the incoming state; FaceBakery applies it around the model center.
    private static final Transformation FLUID_TRANSFORM = new Transformation(null, null, new Vector3f(1, 1, 1.002F), null);
    private static final net.minecraft.client.renderer.block.model.ItemModelGenerator ITEM_MODEL_GENERATOR =
          new net.minecraft.client.renderer.block.model.ItemModelGenerator();

    private final ResourceLocation id;
    private final ResourceLocation fluidId;
    private final BlockModel properties;

    FabricFluidContainerModel(ResourceLocation id, JsonObject root) {
        this.id = id;
        this.fluidId = ResourceLocation.parse(root.get("fluid").getAsString());
        this.properties = BlockModel.fromString(root.toString());
    }

    private TextureAtlasSprite stillSprite(Function<Material, TextureAtlasSprite> spriteGetter) {
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        if (fluid != Fluids.EMPTY && fluid instanceof MekanismFlowingFluid mekanismFluid) {
            return spriteGetter.apply(new Material(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                  mekanismFluid.getMekanismFluidType().stillTexture));
        }
        return spriteGetter.apply(new Material(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
              net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()));
    }

    @Override
    public @NotNull BakedModel bake(@NotNull ModelBaker baker, @NotNull Function<Material, TextureAtlasSprite> spriteGetter, @NotNull ModelState modelState) {
        TextureAtlasSprite baseSprite = spriteGetter.apply(properties.getMaterial("base"));
        TextureAtlasSprite still = stillSprite(spriteGetter);
        TextureAtlasSprite mask = spriteGetter.apply(properties.getMaterial("fluid"));

        List<BakedQuad> quads = new ArrayList<>();
        List<BlockElement> baseElements = ITEM_MODEL_GENERATOR.processFrames(0, "layer0", baseSprite.contents());
        fixItemSeams(baseElements, baseSprite);
        bakeElements(quads, baseElements, baseSprite, modelState);
        List<BlockElement> maskElements = maskElements(mask);
        ModelState fluidState = new ModelState() {
            @Override public Transformation getRotation() { return modelState.getRotation().compose(FLUID_TRANSFORM); }
            @Override public boolean isUvLocked() { return modelState.isUvLocked(); }
        };
        bakeElements(quads, maskElements, still, fluidState);

        TextureAtlasSprite particle = properties.hasTexture("particle") ? spriteGetter.apply(properties.getMaterial("particle")) : still;
        //The official context forces gui3d=false and useBlockLight=false for the fluid containers.
        return new FabricMergedQuadsModel(id, quads, particle, properties.getTransforms(), false, false, false);
    }

    /** The port of the shared face bakery: bakes each element face against the given sprite (BlockModel.bakeFace). */
    private static void bakeElements(List<BakedQuad> quads, List<BlockElement> elements, TextureAtlasSprite sprite, ModelState state) {
        for (BlockElement element : elements) {
            for (Direction direction : element.faces.keySet()) {
                quads.add(BlockModel.bakeFace(element, element.faces.get(direction), sprite, direction, state));
            }
        }
    }

    /** Adapts NeoForge ClientHooks.fixItemModelSeams to the generated bucket edges. */
    private static void fixItemSeams(List<BlockElement> elements, TextureAtlasSprite sprite) {
        float expand = -sprite.uvShrinkRatio();
        for (BlockElement element : elements) {
            if (element.faces.size() != 1) continue;
            var face = element.faces.entrySet().iterator().next();
            if (face.getKey().getAxis() == Direction.Axis.Z) continue;
            element.from.x = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.lerp(expand, element.from.x, 8F), 0F, 16F);
            element.from.y = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.lerp(expand, element.from.y, 8F), 0F, 16F);
            element.to.x = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.lerp(expand, element.to.x, 8F), 0F, 16F);
            element.to.y = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.lerp(expand, element.to.y, 8F), 0F, 16F);
            float[] uv = face.getValue().uv().uvs;
            int first = face.getKey().getAxis() == Direction.Axis.Y ? 0 : 1;
            float center = (uv[first] + uv[first + 2]) / 2F;
            uv[first] = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.lerp(expand, uv[first], center), 0F, 16F);
            uv[first + 2] = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.lerp(expand, uv[first + 2], center), 0F, 16F);
        }
    }

    /**
     * The port of the official mask element scan: the mask texture's opaque rectangles become block elements at the
     * item depth, tint index 1, all faces present.
     */
    private static List<BlockElement> maskElements(TextureAtlasSprite mask) {
        SpriteContents contents = mask.contents();
        //Keep the generated edge faces; only the full front/back slab is replaced by mask rectangles.
        List<BlockElement> elements = new ArrayList<>(ITEM_MODEL_GENERATOR.processFrames(1, "layer1", contents));
        fixItemSeams(elements, mask);
        elements.remove(0);
        int width = contents.width(), height = contents.height();
        BitSet bits = new BitSet(width * height);
        contents.getUniqueFrames().forEach(frame -> {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (!contents.isTransparent(frame, x, y)) {
                        bits.set(x + y * width);
                    }
                }
            }
        });
        for (int y = 0; y < height; y++) {
            int xStart = -1;
            for (int x = 0; x < width; x++) {
                boolean opaque = bits.get(x + y * width);
                if (opaque == (xStart == -1)) {
                    if (xStart == -1) {
                        xStart = x;
                        continue;
                    }
                    int yEnd = y + 1;
                    expand:
                    for (; yEnd < height; yEnd++) {
                        for (int x2 = xStart; x2 <= x; x2++) {
                            if (!bits.get(x2 + yEnd * width)) {
                                break expand;
                            }
                        }
                    }
                    for (int i = xStart; i < x; i++) {
                        for (int j = y; j < yEnd; j++) {
                            bits.clear(i + j * width);
                        }
                    }
                    Map<Direction, BlockElementFace> faces = new HashMap<>();
                    for (Direction direction : Direction.values()) {
                        faces.put(direction, new BlockElementFace(null, 1, "layer1", new BlockFaceUV(null, 0)));
                    }
                    elements.add(new BlockElement(
                          new Vector3f(16 * xStart / (float) width, 16 - 16 * yEnd / (float) height, 7.5F),
                          new Vector3f(16 * x / (float) width, 16 - 16 * y / (float) height, 8.5F),
                          faces, null, true));
                    xStart = -1;
                }
            }
        }
        return elements;
    }

    @Override
    public @NotNull Collection<ResourceLocation> getDependencies() {
        return properties.getDependencies();
    }

    @Override
    public void resolveParents(@NotNull Function<ResourceLocation, UnbakedModel> resolver) {
        properties.resolveParents(resolver);
    }
}
