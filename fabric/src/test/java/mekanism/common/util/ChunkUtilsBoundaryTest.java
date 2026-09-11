package mekanism.common.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fabric-local boundary coverage for the unchanged official {@link ChunkUtils}.
 *
 * <p>The official {@code src/test/java/mekanism/common/util/ChunkUtilsTest.java} is
 * compiled unchanged through the root-test oracle allowlist. This suite adds the
 * direction combinations, zero-crossing ordering, and negative floor-section cases
 * that the official examples do not exercise. Only representable
 * {@code ChunkPos}-sized rectangles are used; callers own the precondition that the
 * inclusive dimensions fit a Java {@code long[]}, which the production code does not
 * validate.</p>
 */
class ChunkUtilsBoundaryTest {

    /** Block coordinates straddling the {@code >> 4} floor-section boundaries. */
    private static final int[] BLOCK_BOUNDARIES = {-17, -16, -1, 0, 15, 16};

    @Test
    void rangeClosedUsesXAsFastCoordinateForEveryDirectionCombination() {
        // All four X/Z sign combinations walk row-major with X inner.
        assertTraversal(0, 0, 2, 2);
        assertTraversal(2, 2, 0, 0);
        assertTraversal(2, 0, 0, 2);
        assertTraversal(0, 2, 2, 0);
        // Single-axis descending ranges and a degenerate 1x1 range.
        assertTraversal(3, 1, 0, 1);
        assertTraversal(1, 3, 1, 0);
        assertTraversal(5, 7, 5, 7);
    }

    @Test
    void rangeClosedCrossingZeroIsInclusiveOrderedAndDuplicateFree() {
        int startX = -2;
        int startZ = -1;
        int endX = 1;
        int endZ = 2;
        long[] actual = ChunkUtils.rangeClosed(startX, startZ, endX, endZ);
        assertArrayEquals(expected(startX, startZ, endX, endZ), actual);
        assertEquals((endX - startX + 1) * (endZ - startZ + 1), actual.length);

        Set<Long> distinct = new HashSet<>();
        for (long position : actual) {
            assertTrue(distinct.add(position), "inclusive range must not repeat a chunk position");
            int chunkX = ChunkPos.getX(position);
            int chunkZ = ChunkPos.getZ(position);
            assertTrue(chunkX >= startX && chunkX <= endX, "chunk X out of inclusive range");
            assertTrue(chunkZ >= startZ && chunkZ <= endZ, "chunk Z out of inclusive range");
        }
    }

    @Test
    void packedBlockToChunkMatchesVanillaFloorSectionsAcrossCartesianBoundaries() {
        for (int blockX : BLOCK_BOUNDARIES) {
            for (int blockZ : BLOCK_BOUNDARIES) {
                int sectionX = SectionPos.blockToSectionCoord(blockX);
                int sectionZ = SectionPos.blockToSectionCoord(blockZ);
                assertEquals(Math.floorDiv(blockX, 16), sectionX, "vanilla X floor-section drift");
                assertEquals(Math.floorDiv(blockZ, 16), sectionZ, "vanilla Z floor-section drift");
                long expectedChunk = ChunkPos.asLong(sectionX, sectionZ);
                for (int blockY : BLOCK_BOUNDARIES) {
                    assertEquals(expectedChunk,
                          ChunkUtils.packedBlockToChunk(BlockPos.asLong(blockX, blockY, blockZ)),
                          () -> "chunk mismatch for block (" + blockX + "," + blockY + "," + blockZ + ")");
                }
            }
        }
    }

    @Test
    void packedYDoesNotAffectChunkResult() {
        int[] yValues = {-2048, -64, -17, -1, 0, 15, 16, 255, 2047};
        for (int blockX : BLOCK_BOUNDARIES) {
            for (int blockZ : BLOCK_BOUNDARIES) {
                long baseline = ChunkUtils.packedBlockToChunk(BlockPos.asLong(blockX, 0, blockZ));
                for (int blockY : yValues) {
                    assertEquals(baseline,
                          ChunkUtils.packedBlockToChunk(BlockPos.asLong(blockX, blockY, blockZ)),
                          "packed Y must not influence the chunk result");
                }
            }
        }
    }

    @Test
    void packedBlockPositionsDecodeToTheirSourceCoordinates() {
        for (int blockX : BLOCK_BOUNDARIES) {
            for (int blockY : BLOCK_BOUNDARIES) {
                for (int blockZ : BLOCK_BOUNDARIES) {
                    long packed = BlockPos.asLong(blockX, blockY, blockZ);
                    assertEquals(blockX, BlockPos.getX(packed));
                    assertEquals(blockY, BlockPos.getY(packed));
                    assertEquals(blockZ, BlockPos.getZ(packed));
                    assertEquals(ChunkPos.asLong(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ)),
                          ChunkUtils.packedBlockToChunk(packed));
                }
            }
        }
    }

    private static void assertTraversal(int startX, int startZ, int endX, int endZ) {
        long[] actual = ChunkUtils.rangeClosed(startX, startZ, endX, endZ);
        assertArrayEquals(expected(startX, startZ, endX, endZ), actual,
              () -> "unexpected traversal (" + startX + "," + startZ + ")->(" + endX + "," + endZ + ")");
        assertEquals((Math.abs(startX - endX) + 1) * (Math.abs(startZ - endZ) + 1), actual.length);
        if (startX != endX) {
            assertEquals(startX + Integer.signum(endX - startX), ChunkPos.getX(actual[1]),
                  "X must advance before Z");
            assertEquals(startZ, ChunkPos.getZ(actual[1]), "X must advance before Z");
        }
    }

    /**
     * Row-major oracle with X as the inner, fast-changing coordinate. This is the
     * inclusive ordering documented by official {@code ChunkUtils.rangeClosed}.
     */
    private static long[] expected(int startX, int startZ, int endX, int endZ) {
        int stepZ = startZ <= endZ ? 1 : -1;
        int stepX = startX <= endX ? 1 : -1;
        List<Long> positions = new ArrayList<>();
        for (int z = startZ; ; z += stepZ) {
            for (int x = startX; ; x += stepX) {
                positions.add(ChunkPos.asLong(x, z));
                if (x == endX) {
                    break;
                }
            }
            if (z == endZ) {
                break;
            }
        }
        long[] packed = new long[positions.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = positions.get(i);
        }
        return packed;
    }
}
