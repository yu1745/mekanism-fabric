package mekanism.client.render.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import mekanism.common.lib.Color;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MekaSuitBakedLightClientTest {

    @Test
    void drawMergesEachBakedLightAndUsesBakedNormals() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        int[] data = new int[32];
        ByteBuffer bytes = ByteBuffer.allocate(32).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 4; i++) {
            bytes.clear();
            bytes.putFloat(i).putFloat(0).putFloat(0).putInt(-1).putFloat(0).putFloat(0);
            bytes.putShort((short) (i == 0 ? 240 : 16)).putShort((short) (i == 1 ? 240 : 32));
            bytes.put((byte) 127).put((byte) 0).put((byte) 0).put((byte) 0).rewind();
            for (int j = 0; j < 8; j++) data[i * 8 + j] = bytes.getInt();
        }
        CapturingConsumer consumer = new CapturingConsumer();
        int environment = 48 | (64 << 16);
        MekaSuitArmor.putQuads(List.of(new BakedQuad(data, -1, Direction.NORTH, null, false)), consumer, new PoseStack().last(),
              environment, 0, Color.WHITE);
        assertEquals(List.of(240 | (64 << 16), 48 | (240 << 16), environment, environment), consumer.lights);
        assertEquals(List.of(1F, 1F, 1F, 1F), consumer.normalX);
    }

    private static class CapturingConsumer implements VertexConsumer {
        final List<Integer> lights = new ArrayList<>();
        final List<Float> normalX = new ArrayList<>();

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            lights.add(light);
            normalX.add(nx);
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
