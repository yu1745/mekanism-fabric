package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import org.junit.jupiter.api.Test;

class FluidContainerModelClientTest {
    @Test
    void inheritedTransformsAndIncomingBakeRotationArePreserved() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var root = JsonParser.parseString("{\"parent\":\"neoforge:item/bucket\",\"fluid\":\"minecraft:water\"}").getAsJsonObject();
        var id = ResourceLocation.fromNamespaceAndPath("mekanism", "item/example_bucket");
        var model = new FabricFluidContainerModel(id, root);
        var bucketId = ResourceLocation.fromNamespaceAndPath("neoforge", "item/bucket");
        var defaultId = ResourceLocation.fromNamespaceAndPath("neoforge", "item/default");
        var parents = Map.of(bucketId, blockModel("assets/neoforge/models/item/bucket.json"),
              defaultId, blockModel("assets/neoforge/models/item/default.json"));
        assertTrue(model.getDependencies().contains(bucketId));
        model.resolveParents(parents::get);
        NativeImage image = new NativeImage(16, 16, false);
        for (int y = 3; y < 12; y++) for (int x = 3; x < 12; x++) image.setPixelRGBA(x, y, -1);
        try (var contents = new SpriteContents(id, new FrameSize(16, 16), image, ResourceMetadata.EMPTY)) {
            var sprite = new TestSprite(id, contents);
            var normal = model.bake(null, material -> sprite, BlockModelRotation.X0_Y0);
            var rotated = model.bake(null, material -> sprite, BlockModelRotation.X0_Y90);
            assertTrue(normal.getTransforms().hasTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND));
            assertEquals(0.68F, normal.getTransforms().firstPersonRightHand.scale.x());
            assertEquals(0.55F, normal.getTransforms().thirdPersonLeftHand.scale.x());
            assertFalse(normal.getQuads(null, null, RandomSource.create()).isEmpty());
            assertNotEquals(normal.getQuads(null, null, RandomSource.create()).get(0).getDirection(),
                  rotated.getQuads(null, null, RandomSource.create()).get(0).getDirection());
            assertTrue(normal.getQuads(null, null, RandomSource.create()).stream().anyMatch(q -> q.getTintIndex() == 1));
        }
    }

    private static BlockModel blockModel(String resource) throws Exception {
        try (var stream = FluidContainerModelClientTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing " + resource);
            return BlockModel.fromStream(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static final class TestSprite extends TextureAtlasSprite {
        TestSprite(ResourceLocation id, SpriteContents contents) { super(id, contents, 16, 16, 0, 0); }
    }
}
