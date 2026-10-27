package mekanism.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import mekanism.client.render.lib.Quad;
import mekanism.client.render.lib.QuadTransformation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class QuadVertexFormatClientTest {

    @Test
    void bakedBlockVerticesSurviveUnpackAndRebake() {
        int[] data = vertices();
        BakedQuad original = new BakedQuad(data, 2, Direction.NORTH, null, true);
        BakedQuad result = new Quad(original).bake();
        assertArrayEquals(data, result.getVertices());
        assertEquals(2, result.getTintIndex());
        assertEquals(Direction.NORTH, result.getDirection());
    }

    @Test
    void fullbrightChangesLightNotColourOrNormal() {
        int[] data = vertices();
        Quad quad = new Quad(new BakedQuad(data, -1, Direction.NORTH, null, false));
        QuadTransformation.fullbright.transform(quad);
        int[] result = quad.bake().getVertices();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 8; j++) {
                if (j != 6) {
                    assertEquals(data[i * 8 + j], result[i * 8 + j]);
                }
            }
            ByteBuffer light = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
            light.putShort((short) 240).putShort((short) 240).rewind();
            assertEquals(light.getInt(), result[i * 8 + 6]);
        }
    }

    private static int[] vertices() {
        int[] data = new int[32];
        ByteBuffer bytes = ByteBuffer.allocate(32).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 4; i++) {
            bytes.clear();
            bytes.putFloat(i + 0.25F).putFloat(i + 0.5F).putFloat(i + 0.75F);
            bytes.put((byte) (10 + i)).put((byte) (60 + i)).put((byte) (120 + i)).put((byte) (200 + i));
            bytes.putFloat(0.125F * i).putFloat(0.25F * i);
            bytes.putShort((short) (16 * i)).putShort((short) (240 - 16 * i));
            bytes.put((byte) 40).put((byte) -70).put((byte) 100).put((byte) 0).rewind();
            for (int j = 0; j < 8; j++) {
                data[i * 8 + j] = bytes.getInt();
            }
        }
        return data;
    }
}
