package mekanism.common.lib.math;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Black-box coverage for positions, ranges, rotations, and mutable quaternion state. */
class SpatialMathTest {

    private static final double EPS = 2E-6;

    @Test
    void vanillaFactoriesFloorGlobalPositionAndRangeValueContract() {
        Pos3D created = Pos3D.create(new BlockPos(-2, 3, 4));
        assertVec(created, -2, 3, 4);
        Pos3D raw = new Pos3D(-1.2, 3.9, -4.01);
        assertVec(raw.floor(), -2, 3, -5);
        GlobalPos global = raw.getGlobalPos(Level.OVERWORLD);
        assertEquals(Level.OVERWORLD, global.dimension());
        assertEquals(BlockPos.containing(-1.2, 3.9, -4.01), global.pos());
        assertVec(Pos3D.create(global), -2, 3, -5);

        Range3D range = new Range3D(-5, -4, 7, 9, Level.NETHER);
        assertAll(() -> assertEquals(-5, range.xMin()), () -> assertEquals(-4, range.zMin()),
              () -> assertEquals(7, range.xMax()), () -> assertEquals(9, range.zMax()),
              () -> assertEquals(Level.NETHER, range.dimension()));
        assertEquals(range, range.clone());
        assertNotSame(range, range.clone());
        assertEquals("[Range3D: -5, -4, 7, 9, dim=minecraft:the_nether]", range.toString());
    }

    @Test
    void positionOperationsRemainImmutableAndPreserveAxisSemantics() {
        Pos3D p = new Pos3D(1, 2, 3);
        assertVec(p.diff(new Vec3(4, 0, -1)), -3, 2, 4);
        assertVec(p.centre(), 1.5, 2.5, 3.5);
        assertVec(p.translate(2, -1, 4), 3, 1, 7);
        assertVec(p.translate(new Vec3(2, 3, 4)), 3, 5, 7);
        assertVec(p.translate(new Vec3(1, 0, 0), new Vec3(0, 2, 3)), 2, 4, 6);
        assertVec(p.translate(Direction.WEST, 2), -1, 2, 3);
        assertVec(p.translateExcludingSide(Direction.UP, 4), 5, 2, 7);
        assertVec(p.multiply(new Vec3(2, 3, 4)), 2, 6, 12);
        assertVec(p.scale(2), 2, 4, 6);
        assertEquals(5, p.distance(new Vec3(4, 6, 3)), EPS);
        assertVec(p.xCrossProduct(), 0, 3, -2);
        assertVec(p.zCrossProduct(), -2, 1, 0);
        assertVec(p.getPerpendicular(), 0, 3, -2);
        assertVec(new Pos3D(1, 2, 0).getPerpendicular(), -2, 1, 0);
        assertEquals(p, p.clone());
        assertNotSame(p, p.clone());
        assertEquals("[Pos3D: 1.0, 2.0, 3.0]", p.toString());
        assertVec(p, 1, 2, 3);
    }

    @Test
    void positionRotationsDistinguishDegreesSignsOrderMatrixAndQuaternion() {
        assertVec(new Pos3D(1, 2, 3).yRot(90), -3, 2, 1);
        assertVec(new Pos3D(1, 2, 3).xRot(90), 1, -3, 2);
        assertVec(new Pos3D(1, 0, 0).rotate(90, 0, 0), 0, 0, 1);
        assertVec(new Pos3D(1, 0, 0).rotate(90, new Pos3D(0, 1, 0)), 0, 0, 1);
        double[] matrix = Pos3D.getRotationMatrix(90, new Pos3D(0, 1, 0));
        assertVec(Pos3D.translateMatrix(matrix, new Pos3D(1, 0, 0)), 0, 0, 1);
        Quaternion q = new Quaternion(new Vec3(0, 1, 0), 90, true);
        assertVec(new Pos3D(1, 0, 0).transform(q), 0, 0, -1);
    }

    @Test
    void quaternionConstructorsAndMutableStateMachineCoverOrderAndNormalization() {
        Quaternion degrees = new Quaternion(new Vec3(0, 1, 0), 90, true);
        Quaternion radians = new Quaternion(new Vec3(0, 1, 0), Math.PI / 2, false);
        assertQuaternion(degrees, radians.getX(), radians.getY(), radians.getZ(), radians.getW());
        assertEquals(Quaternion.ONE, new Quaternion(0, 0, 0, 1));

        Quaternion x = new Quaternion(new Vec3(1, 0, 0), 90, true);
        Quaternion y = new Quaternion(new Vec3(0, 1, 0), 90, true);
        Quaternion xy = x.copy().multiply(y);
        Quaternion yx = y.copy().multiply(x);
        assertQuaternion(xy, 0.5, 0.5, 0.5, 0.5);
        assertQuaternion(yx, 0.5, 0.5, -0.5, 0.5);
        assertNotEquals(xy, yx);
        assertNotEquals(x, x.copy().conjugate());
        assertEquals(x, x.copy().conjugate().conjugate());
        assertEquals(x, x.copy());
        assertNotSame(x, x.copy());

        Quaternion scaled = new Quaternion(1, 2, 3, 4).multiply(2);
        assertQuaternion(scaled, 2, 4, 6, 8);
        scaled.normalize();
        assertEquals(1, scaled.magnitude(), 2E-5);
        Quaternion zero = new Quaternion(0, 0, 0, 0).normalize();
        assertQuaternion(zero, 0, 0, 0, 0);
        Quaternion euler = new Quaternion(90, 0, 0, true);
        assertVec(euler.rotate(new Vec3(0, 1, 0)), 0, 0, 1);
    }

    private static void assertVec(Vec3 actual, double x, double y, double z) {
        assertAll(() -> assertEquals(x, actual.x, EPS), () -> assertEquals(y, actual.y, EPS),
              () -> assertEquals(z, actual.z, EPS));
    }

    private static void assertQuaternion(Quaternion q, double x, double y, double z, double w) {
        assertAll(() -> assertEquals(x, q.getX(), EPS), () -> assertEquals(y, q.getY(), EPS),
              () -> assertEquals(z, q.getZ(), EPS), () -> assertEquals(w, q.getW(), EPS));
    }
}
