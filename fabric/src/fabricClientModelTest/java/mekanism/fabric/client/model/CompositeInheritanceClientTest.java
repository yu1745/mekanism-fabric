package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** Run in a client-side Knot worker: the production BlockModel mixin must actually execute. */
class CompositeInheritanceClientTest {
    @Test
    void ordinaryItemAndNestedChildInheritCompositeGeometry() {
        var id = ResourceLocation.fromNamespaceAndPath("mekanism", "test_composite");
        var quad = new BakedQuad(new int[32], -1, Direction.NORTH, null, true);
        boolean[] childResolved = {false};
        BlockModel geometry = new BlockModel(null, List.of(), Map.of(), null, null, ItemTransforms.NO_TRANSFORMS, List.of()) {
            @Override
            public void resolveParents(Function<ResourceLocation, net.minecraft.client.resources.model.UnbakedModel> resolver) {
                childResolved[0] = true;
                super.resolveParents(resolver);
            }
            @Override
            public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
                return new FabricMergedQuadsModel(id, List.of(), Map.of(Direction.NORTH, List.of(quad)), null,
                      ItemTransforms.NO_TRANSFORMS, true, true, true);
            }
        };
        var composite = new FabricCompositeModel(id, "{\"textures\":{\"particle\":\"minecraft:block/stone\"}}",
              List.of(Pair.of("geometry", geometry)));
        BlockModel item = BlockModel.fromString("{\"parent\":\"mekanism:test_composite\",\"ambientocclusion\":false}");
        item.resolveParents(key -> key.equals(id) ? composite : null);
        assertTrue(childResolved[0]);
        assertTrue((Object) item instanceof CompositeModelParentAccess);
        BakedModel itemBaked = item.bake(null, material -> null, BlockModelRotation.X0_Y0);
        assertSame(quad, itemBaked.getQuads(null, Direction.NORTH, RandomSource.create()).getFirst());
        assertTrue(!itemBaked.useAmbientOcclusion());

        BlockModel nested = BlockModel.fromString("{\"parent\":\"mekanism:test_composite\"}");
        var outer = new FabricCompositeModel(ResourceLocation.fromNamespaceAndPath("mekanism", "outer"),
              "{\"textures\":{\"particle\":\"minecraft:block/stone\"}}", List.of(Pair.of("nested", nested)));
        outer.resolveParents(key -> key.equals(id) ? composite : null);
        BakedModel outerBaked = outer.bake(null, material -> null, BlockModelRotation.X0_Y0);
        assertSame(quad, outerBaked.getQuads(null, Direction.NORTH, RandomSource.create()).getFirst());
    }
}
