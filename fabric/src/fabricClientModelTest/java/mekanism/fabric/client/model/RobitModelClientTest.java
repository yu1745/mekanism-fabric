package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.api.robit.BasicRobitSkin;
import mekanism.client.model.MekanismModelCache;
import mekanism.common.registries.MekanismRobitSkins;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import org.junit.jupiter.api.Test;

class RobitModelClientTest {
    @Test
    void realModelsInheritDisplayAndCacheDatapackSelectedModel() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ResourceLocation baseId = ResourceLocation.fromNamespaceAndPath("mekanism", "item/robit");
        ResourceLocation customId = ResourceLocation.fromNamespaceAndPath("other", "item/custom_robit");
        var base = model(baseId, "assets/mekanism/models/item/robit.json");
        var custom = model(customId, "assets/mekanism/models/item/robit_allay.json");
        custom.resolveParents(id -> {
            assertEquals(baseId, id);
            return base;
        });
        try (var contents = new SpriteContents(baseId, new FrameSize(16, 16), new NativeImage(16, 16, false), ResourceMetadata.EMPTY)) {
            var sprite = new TestSprite(baseId, contents);
            BakedModel baseBaked = base.bake(null, ignored -> sprite, BlockModelRotation.X0_Y0);
            BakedModel customBaked = custom.bake(null, ignored -> sprite, BlockModelRotation.X0_Y0);
            assertTrue(baseBaked.getTransforms().hasTransform(ItemDisplayContext.GUI));
            assertEquals(baseBaked.getTransforms().gui, customBaked.getTransforms().gui);
            assertTrue(baseBaked.getQuads(null, null, RandomSource.create()).size() > 0);
            var customQuads = customBaked.getQuads(null, null, RandomSource.create());
            assertTrue(customQuads.size() > 0);
            int dark = net.minecraft.client.renderer.LightTexture.pack(0, 0);
            int dim = net.minecraft.client.renderer.LightTexture.pack(3, 3);
            int full = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            assertTrue(customQuads.stream().anyMatch(quad -> mekanism.client.model.robit.RobitBakedModel.lightFor(quad, dark) == dim));
            assertTrue(customQuads.stream().anyMatch(quad -> mekanism.client.model.robit.RobitBakedModel.lightFor(quad, dark) == full));
            assertTrue(customQuads.stream().anyMatch(quad -> mekanism.client.model.robit.RobitBakedModel.lightFor(quad, dark) == dark));
            int brightAmbient = net.minecraft.client.renderer.LightTexture.pack(8, 10);
            assertTrue(customQuads.stream().anyMatch(quad -> mekanism.client.model.robit.RobitBakedModel.lightFor(quad, brightAmbient) == brightAmbient));
            AtomicInteger bakes = new AtomicInteger();
            ModelBaker baker = new ModelBaker() {
                @Override public UnbakedModel getModel(ResourceLocation id) { assertEquals(customId, id); return custom; }
                @Override public BakedModel bake(ResourceLocation id, ModelState state) { assertEquals(customId, id); bakes.incrementAndGet(); return customBaked; }
            };
            var cache = MekanismModelCache.INSTANCE;
            cache.clearRobitModels();
            assertNull(cache.getBaseRobit());
            cache.setRobitModelBaker(baseBaked, baker);
            var key = MekanismRobitSkins.BASE;
            var ordinary = new MekanismRobitSkins.SkinLookup(key, new BasicRobitSkin(List.of(baseId)));
            var special = new MekanismRobitSkins.SkinLookup(key, new BasicRobitSkin(List.of(baseId), customId));
            assertSame(baseBaked, cache.getRobitSkin(ordinary));
            assertSame(customBaked, cache.getRobitSkin(special));
            assertNotSame(baseBaked, cache.getRobitSkin(special));
            assertEquals(1, bakes.get());
            cache.clearRobitModels();
            assertNull(cache.getRobitSkin(special));
            cache.setRobitModelBaker(baseBaked, baker);
            assertSame(customBaked, cache.getRobitSkin(special));
            assertEquals(2, bakes.get());
            cache.clearRobitModels();
        }
    }

    private static FabricRobitUnbakedModel model(ResourceLocation id, String path) throws Exception {
        try (var stream = RobitModelClientTest.class.getClassLoader().getResourceAsStream(path)) {
            return new FabricRobitUnbakedModel(id, JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        }
    }

    private static final class TestSprite extends TextureAtlasSprite {
        TestSprite(ResourceLocation id, SpriteContents contents) { super(id, contents, 16, 16, 0, 0); }
    }
}
