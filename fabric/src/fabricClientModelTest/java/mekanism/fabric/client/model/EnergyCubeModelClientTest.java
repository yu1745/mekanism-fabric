package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import mekanism.api.RelativeSide;
import mekanism.common.tile.TileEntityEnergyCube.CubeSideState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class EnergyCubeModelClientTest {
    @Test
    void shippedTierInheritsGeometryAndRespectsRotationAndSideState() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        JsonObject base = model("assets/mekanism/models/block/energy_cube/base.json");
        JsonObject tier = model("assets/mekanism/models/block/energy_cube/basic.json");
        var id = ResourceLocation.fromNamespaceAndPath("mekanism", "block/energy_cube/basic");
        try (var contents = new SpriteContents(id, new FrameSize(16, 16), new NativeImage(16, 16, false), ResourceMetadata.EMPTY)) {
            var sprite = new TestSprite(id, contents);
            var north = FabricEnergyCubeModel.bake(id, tier, base, material -> sprite, BlockModelRotation.X0_Y0);
            var east = FabricEnergyCubeModel.bake(id, tier, base, material -> sprite, BlockModelRotation.X0_Y90);
            var random = RandomSource.create();
            assertFalse(north.isVanillaAdapter());
            assertFalse(north.getQuads(null, null, random).isEmpty());
            assertFalse(north.getQuads(null, Direction.NORTH, random).isEmpty());
            assertNotEquals(north.getQuads(null, null, random), north.getQuads(null, Direction.NORTH, random));
            CubeSideState[] states = new CubeSideState[RelativeSide.values().length];
            Arrays.fill(states, CubeSideState.INACTIVE);
            var inactive = north.dynamic().getSplitQuads(null, states);
            states[RelativeSide.FRONT.ordinal()] = CubeSideState.ACTIVE_LIT;
            var active = north.dynamic().getSplitQuads(null, states);
            assertFalse(active.lit().isEmpty());
            assertTrue(inactive.lit().isEmpty());
            assertNotEquals(north.dynamic().getSplitQuads(Direction.NORTH, states).lit().size(),
                  east.dynamic().getSplitQuads(Direction.NORTH, states).lit().size());
        }
    }

    private static JsonObject model(String resource) throws Exception {
        try (var stream = EnergyCubeModelClientTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing " + resource);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static final class TestSprite extends TextureAtlasSprite {
        TestSprite(ResourceLocation id, SpriteContents contents) { super(id, contents, 16, 16, 0, 0); }
    }
}
