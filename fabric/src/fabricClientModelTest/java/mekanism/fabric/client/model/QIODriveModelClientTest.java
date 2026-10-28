package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class QIODriveModelClientTest {
    @Test
    void shippedDriveAndShellLightMatchesOnlyTheirBakedFacesAcrossRotations() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var id = ResourceLocation.fromNamespaceAndPath("mekanism", "qio_test");
        try (var contents = new SpriteContents(id, new FrameSize(16, 16), new NativeImage(16, 16, false), ResourceMetadata.EMPTY)) {
            var sprite = new TestSprite(id, contents);
            for (String path : new String[]{"block/qio_drive/qio_drive_empty", "block/qio_drive/qio_drive_partial",
                  "block/qio_drive/qio_drive_full", "block/qio_drive_array"}) {
                String file = "assets/mekanism/models/" + path + ".json";
                try (var stream = getClass().getClassLoader().getResourceAsStream(file)) {
                    var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                    BlockModel source = BlockModel.fromString(json.toString());
                    source.resolveParents(ignored -> BlockModel.fromString("{}"));
                    for (var rotation : new BlockModelRotation[]{BlockModelRotation.X0_Y0, BlockModelRotation.X0_Y90}) {
                        var light = FabricQIOModelPlugin.bakedLight(json, source, ignored -> sprite, rotation);
                        var baked = source.bake(null, ignored -> sprite, rotation);
                        var quads = new ArrayList<>(baked.getQuads(null, null, RandomSource.create()));
                        for (Direction direction : Direction.values()) {
                            quads.addAll(baked.getQuads(null, direction, RandomSource.create()));
                        }
                        assertTrue(!light.isEmpty(), path);
                        assertTrue(light.stream().allMatch(entry -> entry.light() == 0xF000F0), path);
                        assertTrue(light.stream().allMatch(entry -> quads.stream().anyMatch(entry::matches)), path);
                        assertTrue(quads.stream().anyMatch(quad -> light.stream().noneMatch(entry -> entry.matches(quad))), path);
                        assertEquals(path.endsWith("qio_drive_array") ? 4 : 5, light.size(), path);
                    }
                }
            }
        }
    }

    private static final class TestSprite extends TextureAtlasSprite {
        TestSprite(ResourceLocation id, SpriteContents contents) { super(id, contents, 16, 16, 0, 0); }
    }
}
