package mekanism.fabric.client.model;

import com.google.gson.JsonObject;
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
 * Fabric port of the official {@code neoforge:fluid_container} model (the chemical buckets). The neoforge base model
 * {@code neoforge:item/bucket} exists solely in the NeoForge jar, so this model rebuilds the whole stack: the flat
 * item sheath from the vanilla bucket sprite (the official base layer) plus the fluid surface - rectangular elements
 * scanned from the bucket mask texture, baked against the fluid's still texture with the depth nudge of the official
 * FLUID_TRANSFORM, and tint index 1 for the fluid color (the official {@code Colors} item color handles the tint).
 */
public final class FabricFluidContainerModel implements UnbakedModel {

    private static final ResourceLocation BUCKET_BASE = ResourceLocation.withDefaultNamespace("item/bucket");
    private static final ResourceLocation BUCKET_FLUID_MASK = ResourceLocation.fromNamespaceAndPath("neoforge", "item/mask/bucket_fluid");
    //The official FLUID_TRANSFORM scales depth by 1.002 to keep the fluid surface off the base sheath (z 7.5..8.5).
    private static final float FLUID_DEPTH_OFFSET = 0.017F;
    private static final net.minecraft.client.renderer.block.model.ItemModelGenerator ITEM_MODEL_GENERATOR =
          new net.minecraft.client.renderer.block.model.ItemModelGenerator();

    private final ResourceLocation id;
    private final ResourceLocation fluidId;

    FabricFluidContainerModel(ResourceLocation id, JsonObject root) {
        this.id = id;
        this.fluidId = ResourceLocation.parse(root.get("fluid").getAsString());
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
        Function<Material, TextureAtlasSprite> blocks = mat -> spriteGetter.apply(new Material(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, mat.texture()));
        TextureAtlasSprite baseSprite = blocks.apply(new Material(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, BUCKET_BASE));
        TextureAtlasSprite still = stillSprite(spriteGetter);
        TextureAtlasSprite mask = blocks.apply(new Material(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, BUCKET_FLUID_MASK));

        List<BakedQuad> quads = new ArrayList<>();
        //Base layer: the flat item sheath from the vanilla bucket sprite (tint index 0).
        List<BlockElement> baseElements = ITEM_MODEL_GENERATOR.processFrames(0, "layer0", baseSprite.contents());
        bakeElements(quads, baseElements, baseSprite, blocks, 0.0F);
        //Fluid layer: rectangles scanned from the mask, baked with the still texture, tint index 1.
        List<BlockElement> maskElements = maskElements(mask.contents());
        bakeElements(quads, maskElements, still, blocks, FLUID_DEPTH_OFFSET);

        //The official context forces gui3d=false and useBlockLight=false for the fluid containers.
        return new FabricMergedQuadsModel(id, quads, baseSprite, ItemTransforms.NO_TRANSFORMS, false, false, false);
    }

    /** The port of the shared face bakery: bakes each element face against the given sprite (BlockModel.bakeFace). */
    private static void bakeElements(List<BakedQuad> quads, List<BlockElement> elements, TextureAtlasSprite sprite,
          Function<Material, TextureAtlasSprite> spriteGetter, float depthOffset) {
        for (BlockElement element : elements) {
            for (Direction direction : element.faces.keySet()) {
                BlockElementFace original = element.faces.get(direction);
                if (depthOffset != 0.0F) {
                    //Nudge the depth axis only (the official FLUID_TRANSFORM's scale(1, 1, 1.002) port).
                    Vector3f from = new Vector3f(element.from);
                    Vector3f to = new Vector3f(element.to);
                    from.z += depthOffset;
                    to.z += depthOffset;
                    BlockElement shifted = new BlockElement(from, to, element.faces, element.rotation, element.shade);
                    quads.add(BlockModel.bakeFace(shifted, original, sprite, direction, net.minecraft.client.resources.model.BlockModelRotation.X0_Y0));
                } else {
                    quads.add(BlockModel.bakeFace(element, original, sprite, direction, net.minecraft.client.resources.model.BlockModelRotation.X0_Y0));
                }
            }
        }
    }

    /**
     * The port of the official mask element scan: the mask texture's opaque rectangles become block elements at the
     * item depth, tint index 1, all faces present.
     */
    private static List<BlockElement> maskElements(SpriteContents contents) {
        List<BlockElement> elements = new ArrayList<>();
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
        //The neoforge parent chain is reconstructed here, nothing to resolve.
        return List.of();
    }

    @Override
    public void resolveParents(@NotNull Function<ResourceLocation, UnbakedModel> resolver) {
    }
}
