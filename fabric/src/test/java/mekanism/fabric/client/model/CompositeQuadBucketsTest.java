package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class CompositeQuadBucketsTest {
    @Test
    void mergedModelKeepsUnculledAndCulledQuadsSeparate() {
        var unculled = new BakedQuad(new int[32], -1, Direction.UP, null, true);
        var north = new BakedQuad(new int[32], -1, Direction.NORTH, null, true);
        var model = new FabricMergedQuadsModel(ResourceLocation.fromNamespaceAndPath("mekanism", "test"),
              List.of(unculled), Map.of(Direction.NORTH, List.of(north)), null, ItemTransforms.NO_TRANSFORMS, true, true, true);
        var random = RandomSource.create();
        assertSame(unculled, model.getQuads(null, null, random).getFirst());
        assertSame(north, model.getQuads(null, Direction.NORTH, random).getFirst());
        assertEquals(List.of(), model.getQuads(null, Direction.SOUTH, random));
    }
}
