package mekanism.common.util;

import java.util.Arrays;
import java.util.List;
import mekanism.common.util.MultipartUtils.AdvancedRayTraceResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Direct ray tests avoid entity/player, world, registry, and collision-owner fixtures. */
class MultipartUtilsTest {

    @Test
    void collisionChoosesClosestShapeInWorldSpaceAndRetainsOfficialSubHitIndex() {
        BlockPos pos = new BlockPos(10, 20, 30);
        Vec3 start = new Vec3(9, 20.5, 30.5);
        Vec3 end = new Vec3(13, 20.5, 30.5);
        VoxelShape far = Shapes.box(0.75, 0, 0, 1, 1, 1);
        VoxelShape near = Shapes.box(0, 0, 0, 0.25, 1, 1);

        AdvancedRayTraceResult hit = MultipartUtils.collisionRayTrace(pos, start, end,
              Arrays.asList(null, far, near));
        assertNotNull(hit);
        assertSame(near, hit.bounds);
        assertEquals(1, hit.subHit, "counter starts at -1 and includes null collection positions");
        assertEquals(new Vec3(10, 20.5, 30.5), hit.hit.getLocation());

        AdvancedRayTraceResult reversed = MultipartUtils.collisionRayTrace(pos, start, end, List.of(far, near));
        assertSame(near, reversed.bounds);
        assertEquals(0, reversed.subHit);
        AdvancedRayTraceResult equalFirst = MultipartUtils.collisionRayTrace(pos, start, end, List.of(near, near));
        assertEquals(-1, equalFirst.subHit, "strict less-than keeps the first equal-distance hit");
    }

    @Test
    void emptyAndMissReturnNullWhileAdvancedResultPreservesNestedIdentityAndDistance() {
        BlockPos pos = BlockPos.ZERO;
        Vec3 start = new Vec3(-2, 0.5, 0.5);
        Vec3 end = new Vec3(2, 0.5, 0.5);
        assertNull(MultipartUtils.collisionRayTrace(pos, start, end, List.of()));
        assertNull(MultipartUtils.collisionRayTrace(pos, start, end,
              List.of(Shapes.box(0, 2, 0, 1, 3, 1))));

        VoxelShape shape = Shapes.block();
        BlockHitResult nested = shape.clip(start, end, pos);
        assertNotNull(nested);
        AdvancedRayTraceResult result = new AdvancedRayTraceResult(nested, shape, 7);
        assertTrue(result.valid());
        assertSame(nested, result.hit);
        assertSame(shape, result.bounds);
        assertEquals(7, result.subHit);
        assertEquals(nested.getLocation().distanceToSqr(end), result.squareDistanceTo(end), 1E-12);
        assertFalse(new AdvancedRayTraceResult(null, shape, 0).valid());
        assertFalse(new AdvancedRayTraceResult(nested, null, 0).valid());
    }
}
