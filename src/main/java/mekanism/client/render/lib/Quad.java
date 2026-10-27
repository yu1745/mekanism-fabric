package mekanism.client.render.lib;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;
import mekanism.common.lib.Color;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import org.joml.Vector3f;

public class Quad {

    private final Vertex[] vertices;
    private Direction side;
    private TextureAtlasSprite sprite;
    private int tintIndex;
    private boolean shade;
    private boolean hasAmbientOcclusion;

    public Quad(TextureAtlasSprite sprite, Direction side, Vertex[] vertices) {
        this(sprite, side, vertices, -1, false, true);
    }

    public Quad(TextureAtlasSprite sprite, Direction side, Vertex[] vertices, int tintIndex, boolean shade, boolean hasAmbientOcclusion) {
        this.sprite = sprite;
        this.side = side;
        this.vertices = vertices;
        this.tintIndex = tintIndex;
        this.shade = shade;
        this.hasAmbientOcclusion = hasAmbientOcclusion;
    }

    public Quad(BakedQuad quad) {
        side = quad.getDirection();
        sprite = quad.getSprite();
        tintIndex = quad.getTintIndex();
        shade = quad.isShade();
        //Fabric port: vanilla 1.21.1 BakedQuad carries no per-quad ambient occlusion flag; the port models treat
        //it as absent (the energy cube draws with the block AO of its render layer).
        // BLOCK vertices contain RGBA bytes, UV, light shorts and a packed normal in native byte order.
        // VertexConsumer.putBulkData substitutes draw-time colour/light and therefore cannot unpack them.
        int[] data = quad.getVertices();
        vertices = new Vertex[4];
        ByteBuffer bytes = ByteBuffer.allocate(32).order(ByteOrder.nativeOrder());
        Vec3i faceNormal = side.getNormal();
        for (int i = 0; i < vertices.length; i++) {
            bytes.clear();
            for (int j = 0; j < 8; j++) {
                bytes.putInt(data[i * 8 + j]);
            }
            float nx = bytes.get(28) / 127.0F, ny = bytes.get(29) / 127.0F, nz = bytes.get(30) / 127.0F;
            if (nx == 0 && ny == 0 && nz == 0) {
                // Vanilla baked faces commonly leave normals unset; the face direction is their draw normal.
                nx = faceNormal.getX();
                ny = faceNormal.getY();
                nz = faceNormal.getZ();
            }
            vertices[i] = new Vertex().pos(new Vector3f(bytes.getFloat(0), bytes.getFloat(4), bytes.getFloat(8)))
                  .color(bytes.get(12) & 0xFF, bytes.get(13) & 0xFF, bytes.get(14) & 0xFF, bytes.get(15) & 0xFF)
                  .texRaw(bytes.getFloat(16), bytes.getFloat(20))
                  .overlay(OverlayTexture.NO_OVERLAY & 0xFFFF, OverlayTexture.NO_OVERLAY >>> 16)
                  .lightRaw(bytes.getShort(24) & 0xFFFF, bytes.getShort(26) & 0xFFFF)
                  .normal(nx, ny, nz);
        }
    }

    public TextureAtlasSprite getTexture() {
        return sprite;
    }

    public void setTexture(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    public int getTint() {
        return tintIndex;
    }

    public void setTint(int tintIndex) {
        this.tintIndex = tintIndex;
    }

    public void vertexTransform(Consumer<Vertex> transformation) {
        for (Vertex v : vertices) {
            transformation.accept(v);
        }
    }

    public boolean transform(QuadTransformation... transformations) {
        boolean transformed = false;
        for (QuadTransformation transform : transformations) {
            transformed |= transform.transform(this);
        }
        return transformed;
    }

    public Vertex[] getVertices() {
        return vertices;
    }

    public void setSide(Direction side) {
        this.side = side;
    }

    public Direction getSide() {
        return side;
    }

    public boolean isShade() {
        return shade;
    }

    public void setShade(boolean shade) {
        this.shade = shade;
    }

    public boolean hasAmbientOcclusion() {
        return hasAmbientOcclusion;
    }

    public void setHasAmbientOcclusion(boolean hasAmbientOcclusion) {
        this.hasAmbientOcclusion = hasAmbientOcclusion;
    }

    @SuppressWarnings("unused")
    public BakedQuad bake() {
        // Vanilla BLOCK has no overlay element: position, RGBA, UV, light, normal and padding.
        int[] vertexData = new int[32];
        ByteBuffer bytes = ByteBuffer.allocate(32).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 4; i++) {
            Vertex vertex = vertices[i];
            bytes.clear();
            bytes.putFloat(vertex.getPos().x()).putFloat(vertex.getPos().y()).putFloat(vertex.getPos().z());
            bytes.put((byte) vertex.getRed()).put((byte) vertex.getGreen()).put((byte) vertex.getBlue()).put((byte) vertex.getAlpha());
            bytes.putFloat(vertex.getTexU()).putFloat(vertex.getTexV());
            bytes.putShort((short) vertex.getRawLightU()).putShort((short) vertex.getRawLightV());
            Vector3f normal = vertex.getNormal();
            bytes.put(packNormal(normal.x())).put(packNormal(normal.y())).put(packNormal(normal.z())).put((byte) 0);
            bytes.rewind();
            for (int j = 0; j < 8; j++) {
                vertexData[i * 8 + j] = bytes.getInt();
            }
        }
        return new BakedQuad(vertexData, tintIndex, side, sprite, shade);
    }

    public Quad copy() {
        Vertex[] newVertices = new Vertex[vertices.length];
        for (int i = 0; i < newVertices.length; i++) {
            newVertices[i] = vertices[i].copy(true);
        }
        return new Quad(sprite, side, newVertices, tintIndex, shade, hasAmbientOcclusion);
    }

    public Quad flip() {
        Vertex[] flipped = new Vertex[vertices.length];
        for (int i = 0; i < flipped.length; i++) {
            flipped[i] = vertices[i].flip();
        }
        return new Quad(sprite, side.getOpposite(), flipped, tintIndex, shade, hasAmbientOcclusion);
    }

    private static byte packNormal(float value) {
        return (byte) Math.round(Math.max(-1.0F, Math.min(1.0F, value)) * 127.0F);
    }

    public static class Builder {

        private TextureAtlasSprite texture;
        private final Direction side;
        private Color color = Color.WHITE;

        private Vector3f vec1, vec2, vec3, vec4;

        private float minU, minV, maxU, maxV;
        private int lightU, lightV;

        private int tintIndex = -1;
        private boolean shade;
        private boolean hasAmbientOcclusion = true;
        private boolean contractUVs = true;

        public Builder(TextureAtlasSprite texture, Direction side) {
            this.texture = texture;
            this.side = side;
        }

        public Builder light(int light) {
            return light(LightTexture.block(light), LightTexture.sky(light));
        }

        public Builder light(int u, int v) {
            this.lightU = u;
            this.lightV = v;
            return this;
        }

        public Builder uv(float minU, float minV, float maxU, float maxV) {
            this.minU = minU;
            this.minV = minV;
            this.maxU = maxU;
            this.maxV = maxV;
            return this;
        }

        public Builder tex(TextureAtlasSprite texture) {
            this.texture = texture;
            return this;
        }

        public Builder tint(int tintIndex) {
            this.tintIndex = tintIndex;
            return this;
        }

        public Builder color(Color color) {
            this.color = color;
            return this;
        }

        public Builder setShade(boolean shade) {
            this.shade = shade;
            return this;
        }

        public Builder setHasAmbientOcclusion(boolean hasAmbientOcclusion) {
            this.hasAmbientOcclusion = hasAmbientOcclusion;
            return this;
        }

        public Builder contractUVs(boolean contractUVs) {
            this.contractUVs = contractUVs;
            return this;
        }

        public Builder pos(Vector3f tl, Vector3f bl, Vector3f br, Vector3f tr) {
            this.vec1 = tl;
            this.vec2 = bl;
            this.vec3 = br;
            this.vec4 = tr;
            return this;
        }

        public Builder rect(Vector3f start, float width, float height) {
            return rect(start, width, height, 1F / 16F); // default to 1/16 scale
        }

        // start = bottom left
        public Builder rect(Vector3f start, float width, float height, float scale) {
            start = start.mul(scale, scale, scale, new Vector3f());
            Vector3f end;
            if (side.getAxis().isHorizontal()) {
                Vec3i normal = side.getNormal();
                end = start.add(normal.getZ() * width * scale, 0, normal.getX() * width * scale, new Vector3f());
                if (side.getAxis() == Axis.X) {
                    //Wind vertices in a different order so that it faces the correct direction
                    return pos(start, start.add(0, height * scale, 0, new Vector3f()),
                          end.add(0, height * scale, 0, new Vector3f()), end);
                }
            } else {
                end = new Vector3f(start.x + width * scale, start.y, start.z);
            }
            return pos(start.add(0, height * scale, 0, new Vector3f()), start,
                  end, end.add(0, height * scale, 0, new Vector3f()));
        }

        public Quad build() {
            Vertex[] vertices = new Vertex[4];
            //Note: We don't need to create a new Vector3f for the cross multiplication, as it will just mutate the new one we used for the first subtraction
            Vector3f normal = vec3.sub(vec2, new Vector3f()).cross(vec1.sub(vec2,  new Vector3f())).normalize();
            vertices[0] = Vertex.create(vec1, normal, color, texture, minU, minV).light(lightU, lightV);
            vertices[1] = Vertex.create(vec2, normal, color, texture, minU, maxV).light(lightU, lightV);
            vertices[2] = Vertex.create(vec3, normal, color, texture, maxU, maxV).light(lightU, lightV);
            vertices[3] = Vertex.create(vec4, normal, color, texture, maxU, minV).light(lightU, lightV);
            Quad quad = new Quad(texture, side, vertices, tintIndex, shade, hasAmbientOcclusion);
            if (contractUVs) {
                QuadUtils.contractUVs(quad);
            }
            return quad;
        }
    }
}
