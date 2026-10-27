package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class QIODataBasedModelClientTest {
    @Test
    void shippedModelsBakeBothStatesForEachRotationAndRecoverLights() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ResourceLocation id = id("block/qio_redstone_adapter");
        ResourceLocation unlitId = id("block/qio_redstone_adapter_unlit");
        ResourceLocation litId = id("block/qio_redstone_adapter_lit");
        BlockModel unlit = block("assets/mekanism/models/block/qio_redstone_adapter_unlit.json");
        BlockModel lit = block("assets/mekanism/models/block/qio_redstone_adapter_lit.json");
        BlockModel parent = BlockModel.fromString("{}");
        Map<ResourceLocation, UnbakedModel> models = Map.of(unlitId, unlit, litId, lit, id("block/flat_model"), parent);
        var root = JsonParser.parseReader(new InputStreamReader(resource("assets/mekanism/models/block/qio_redstone_adapter.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        ResourceManager resources = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
            if (method.getName().equals("getResource")) {
                ResourceLocation file = (ResourceLocation) args[0];
                return Optional.of(new Resource(null, () -> resource("assets/" + file.getNamespace() + "/" + file.getPath())));
            }
            throw new UnsupportedOperationException(method.getName());
        });
        FabricDataBasedUnbakedModel source = new FabricDataBasedUnbakedModel(id, root, resources);
        source.resolveParents(models::get);
        try (var contents = new SpriteContents(id, new FrameSize(16, 16), new NativeImage(16, 16, false), ResourceMetadata.EMPTY)) {
            var sprite = new TestSprite(id, contents);
            var upright = (FabricDataBasedUnbakedModel.DataBasedModel) source.bake(null, material -> sprite, BlockModelRotation.X0_Y0);
            var rotated = (FabricDataBasedUnbakedModel.DataBasedModel) source.bake(null, material -> sprite, BlockModelRotation.X90_Y0);
            assertFalse(upright.noDataLight().isEmpty());
            assertTrue(upright.emittingLight().size() > upright.noDataLight().size());
            assertFalse(upright.noData().getQuads(null, Direction.DOWN, RandomSource.create()).isEmpty());
            var uprightQuad = upright.emitting().getQuads(null, null, RandomSource.create()).getFirst();
            var rotatedQuad = rotated.emitting().getQuads(null, null, RandomSource.create()).getFirst();
            assertNotEquals(java.util.Arrays.toString(uprightQuad.getVertices()), java.util.Arrays.toString(rotatedQuad.getVertices()));
        }
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("mekanism", path); }
    private static java.io.InputStream resource(String path) {
        var stream = QIODataBasedModelClientTest.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) throw new IllegalStateException("Missing " + path);
        return stream;
    }
    private static BlockModel block(String path) throws Exception {
        try (var stream = resource(path)) {
            return BlockModel.fromStream(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
    private static final class TestSprite extends TextureAtlasSprite {
        TestSprite(ResourceLocation id, SpriteContents contents) { super(id, contents, 16, 16, 0, 0); }
    }
}
