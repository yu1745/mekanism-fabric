package mekanism.common.lib;

import mekanism.common.lib.effect.CustomEffect;
import mekanism.common.util.StatUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Black-box coverage for the platform-neutral color, statistics, and effect closure. */
class ColorEffectStatTest {

    private static final double EPS = 1E-9;

    @Test
    void colorConstructionPackingChannelsAlphaAndRawRounding() {
        Color c = Color.rgbai(0x12, 0x34, 0x56, 0x78);
        assertArrayEquals(new int[]{0x12, 0x34, 0x56, 0x78}, c.rgbaArray());
        assertArrayEquals(new int[]{0x78, 0x12, 0x34, 0x56}, c.argbArray());
        assertArrayEquals(new int[]{0x12, 0x34, 0x56}, c.rgbArray());
        assertEquals(0x12345678, c.rgba());
        assertEquals(0x78123456, c.argb());
        assertEquals(0x123456, c.rgb());
        assertEquals(c, Color.rgba(c.rgba()));
        assertEquals(c, Color.argb(c.argb()));
        assertEquals(Color.rgbi(0x12, 0x34, 0x56), Color.rgb(c.rgb()));
        assertEquals(c, Color.rgba(c.rgbaArray()));
        assertEquals(c, Color.argb(c.argbArray()));
        assertEquals(Color.rgbi(0x12, 0x34, 0x56), Color.rgb(c.rgbArray()));
        assertEquals(0xFF123456, Color.packOpaque(0x123456));
        assertEquals(0x44332211, Color.argbToFromABGR(0x44112233));

        Color raw = Color.rgbad(1.1, -0.1, 0.5, 2).alpha(-0.25);
        assertEquals(281, raw.r());
        assertEquals(-25, raw.g());
        assertEquals(128, raw.b());
        assertEquals(-64, raw.a());
        assertEquals(1.1, raw.rd(), EPS);
        assertEquals(-0.25, raw.ad(), EPS);
    }

    @Test
    void colorBlendCompositeDarkenAndTintMath() {
        Color from = Color.rgbad(0.2, 0.4, 0.6, 0.25);
        Color to = Color.rgbad(0.8, 0.2, 0.0, 0.75);
        assertEquals(from, from.blend(to, 0));
        assertEquals(to, from.blend(to, 1));
        assertColor(from.blend(to, 0.25), 0.35, 0.35, 0.45, 0.375);
        assertColor(from.blendOnto(to), 0.65, 0.25, 0.15, 0.8125);
        assertEquals(from.blendOnto(to), Color.blend(from, to));
        assertColor(from.darken(0.25), 0.15, 0.3, 0.45, 0.25);
        assertColor(from.toTint(), 0.8, 0.85, 0.9, 1);
    }

    @Test
    void colorHsvConversionsCoverPrimariesGrayAndHueWrapping() {
        assertColor(Color.hsv(0, 1, 1), 1, 0, 0, 1);
        assertColor(Color.hsv(120, 1, 1), 0, 1, 0, 1);
        assertColor(Color.hsv(240, 1, 1), 0, 0, 1, 1);
        assertColor(Color.hsv(420, 1, 1), 1, 1, 0, 1);
        assertColor(Color.hsv(-60, 1, 1), 1, 0, 1, 1);
        assertArrayEquals(new double[]{0, 0, 0.4}, Color.rgbd(0.4, 0.4, 0.4).hsvArray(), EPS);
        assertArrayEquals(new double[]{330, 2D / 3D, 0.9}, Color.rgbd(0.9, 0.3, 0.6).hsvArray(), EPS);
    }

    @Test
    void colorFunctionsExposeScaleHeatBranchesAndValueIdentity() {
        Color from = Color.rgbd(0, 0.5, 1);
        Color to = Color.rgbd(1, 0.5, 0);
        assertColor(Color.ColorFunction.scale(from, to).getColor(0.25F), 0.25, 0.5, 0.75, 1);
        assertArrayEquals(new int[]{40, 200, 0, 255}, Color.ColorFunction.HEAT.getColor(0.1F).rgbaArray());
        assertArrayEquals(new int[]{160, 200, 0, 255}, Color.ColorFunction.HEAT.getColor(0.4F).rgbaArray());
        assertArrayEquals(new int[]{200, 120, 0, 255}, Color.ColorFunction.HEAT.getColor(0.7F).rgbaArray());
        assertEquals(Color.rgbad(0.1, 0.2, 0.3, 0.4), Color.rgbad(0.1, 0.2, 0.3, 0.4));
        assertEquals(Color.rgbad(0.1, 0.2, 0.3, 0.4).hashCode(), Color.rgbad(0.1, 0.2, 0.3, 0.4).hashCode());
        assertNotEquals(Color.rgbad(0.1, 0.2, 0.3, 0.4), Color.rgbad(0.1, 0.2, 0.3, 0.5));
    }

    @Test
    void statisticsMinMaxWrappingAndDeterministicPoissonZero() {
        assertEquals(-7.5, StatUtils.min(3, -7.5, 2, 9));
        assertEquals(9, StatUtils.max(3, -7.5, 2, 9));
        assertEquals(359, StatUtils.wrapDegrees(-1));
        assertEquals(1, StatUtils.wrapDegrees(721));
        assertEquals(Float.floatToRawIntBits(-0.0F), Float.floatToRawIntBits(StatUtils.wrapDegrees(-720)));
        assertEquals(0, StatUtils.inversePoisson(0));
    }

    @Test
    void customEffectDefaultsSettersIdentityGridTickAndStableRandomVector() {
        ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/test.png");
        ExposedEffect effect = new ExposedEffect(texture, 8);
        assertSame(texture, effect.getTexture());
        assertEquals(8, effect.getTextureGridSize());
        assertEquals(Vec3.ZERO, effect.getPos(0.5F));
        assertEquals(Color.WHITE, effect.getColor());
        assertEquals(1, effect.getScale());
        Vec3 pos = new Vec3(1, 2, 3);
        Color color = Color.rgbd(0.2, 0.3, 0.4);
        effect.setPos(pos);
        effect.setColor(color);
        effect.setScale(2.5F);
        assertSame(pos, effect.getPos(0));
        assertSame(color, effect.getColor());
        assertEquals(2.5F, effect.getScale());
        assertFalse(effect.tick());
        assertEquals(1, effect.ticker());
        Vec3 random = effect.randomVector();
        assertTrue(Double.isFinite(random.x) && Double.isFinite(random.y) && Double.isFinite(random.z));
        assertEquals(1, random.length(), 1E-6);
        assertEquals(4, new ExposedEffect(texture).getTextureGridSize());
    }

    private static void assertColor(Color color, double r, double g, double b, double a) {
        assertAll(() -> assertEquals(r, color.rd(), EPS), () -> assertEquals(g, color.gd(), EPS),
              () -> assertEquals(b, color.bd(), EPS), () -> assertEquals(a, color.ad(), EPS));
    }

    private static final class ExposedEffect extends CustomEffect {
        private ExposedEffect(ResourceLocation texture) { super(texture); }
        private ExposedEffect(ResourceLocation texture, int grid) { super(texture, grid); }
        private int ticker() { return ticker; }
        private Vec3 randomVector() { return randVec(); }
    }
}
