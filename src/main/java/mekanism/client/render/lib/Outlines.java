package mekanism.client.render.lib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mekanism.common.util.EnumUtils;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;
import org.joml.Vector3f;

public class Outlines {

    public static List<Line> extract(BakedModel model, @Nullable BlockState state, RandomSource rand) {
        Set<Line> lines = new HashSet<>();
        VertexExtractor consumer = new VertexExtractor(lines);
        for (Direction direction : EnumUtils.DIRECTIONS) {
            for (BakedQuad quad : model.getQuads(state, direction, rand)) {
                consumer.unpack(quad);
            }
        }

        for (BakedQuad quad : model.getQuads(state, null, rand)) {
            consumer.unpack(quad);
        }
        return new ArrayList<>(lines);
    }

    //modified version of VertexConsumer
    @MethodsReturnNonnullByDefault
    private static class VertexExtractor {

        final Set<Line> lines;
        final Vector3f[] vertices = new Vector3f[4];
        int vertexIndex = 0;

        private VertexExtractor(Set<Line> lines) {
            this.lines = lines;
        }

        public void vertex(float pX, float pY, float pZ) {
            vertices[vertexIndex++] = new Vector3f(pX, pY, pZ);
            if (vertexIndex == 4) {
                vertexIndex = 0;
                lines.add(Line.from(vertices[0], vertices[1]));
                lines.add(Line.from(vertices[1], vertices[2]));
                lines.add(Line.from(vertices[2], vertices[3]));
                lines.add(Line.from(vertices[3], vertices[0]));
                Arrays.fill(vertices, null);
            }
        }

        //Based on how NeoForge's quad transformers extract the vertex positions: the baked quad int array packs
        //vertices in DefaultVertexFormat.BLOCK order, so the stride and position offset derive from the format.
        public void unpack(BakedQuad pQuad) {
            int[] quadVertices = pQuad.getVertices();
            int stride = DefaultVertexFormat.BLOCK.getVertexSize() / 4;
            int position = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.POSITION) / 4;
            for (int i = 0; i < 4; i++) {
                int offset = i * stride + position;
                float x = Float.intBitsToFloat(quadVertices[offset]);
                float y = Float.intBitsToFloat(quadVertices[offset + 1]);
                float z = Float.intBitsToFloat(quadVertices[offset + 2]);
                this.vertex(x, y, z);
            }
        }
    }

    public record Line(float x1, float y1, float z1, float x2, float y2, float z2, float nX, float nY, float nZ, int hash) {

        public static Line from(Vector3f v1, Vector3f v2) {
            // normalise by the distance between the points
            float nX = v2.x - v1.x;
            float nY = v2.y - v1.y;
            float nZ = v2.z - v1.z;
            float scalar = Math.invsqrt(Math.fma(nX, nX, Math.fma(nY, nY, nZ * nZ)));
            nX = nX * scalar;
            nY = nY * scalar;
            nZ = nZ * scalar;
            return new Line(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z, nX, nY, nZ, calculateHash(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z));
        }

        private static int calculateHash(float x1, float y1, float z1, float x2, float y2, float z2) {
            //Supports up to a scale of 0.005 in the json (which the miner uses for LEDs)
            int result = Long.hashCode((long) Math.min(x1, x2) * 3_200);
            result = 31 * result + Long.hashCode((long) Math.min(y1, y2) * 3_200);
            result = 31 * result + Long.hashCode((long) Math.min(z1, z2) * 3_200);
            result = 31 * result + Long.hashCode((long) Math.max(x1, x2) * 3_200);
            result = 31 * result + Long.hashCode((long) Math.max(x1, x2) * 3_200);
            result = 31 * result + Long.hashCode((long) Math.max(x1, x2) * 3_200);
            return result;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @SuppressWarnings("SuspiciousNameCombination")
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj == null || obj.getClass() != Line.class) {
                return false;
            }
            Line other = (Line) obj;
            return (Mth.equal(x1, other.x1) && Mth.equal(y1, other.y1) && Mth.equal(z1, other.z1) && Mth.equal(x2, other.x2) && Mth.equal(y2, other.y2) && Mth.equal(z2, other.z2)) ||
                   (Mth.equal(x1, other.x2) && Mth.equal(y1, other.y2) && Mth.equal(z1, other.z2) && Mth.equal(x2, other.x1) && Mth.equal(y2, other.y1) && Mth.equal(z2, other.z1));
        }
    }
}
