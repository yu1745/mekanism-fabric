package mekanism.fabric.client.render.obj;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Narrow baker turning {@link TransmitterObjGeometry} groups into vanilla {@link BakedQuad}s for the transmitter
 * renderer. UVs are mapped through {@link TextureAtlasSprite#getU}/{@code getV} so alias textures resolve against the
 * block atlas; the material's {@code forge_TintIndex} is preserved on the quad (index 1 = logistical color), and
 * per-part rotation (end-icon status rotation about the connection axis) is applied on the CPU before packing.
 *
 * <p>Vertex data layout follows the vanilla 1.21.1 BLOCK format: position(3f), color(4ub), uv0(2f), light(2s), pad.</p>
 */
public final class TransmitterQuadBaker {

    private static final int INTS_PER_VERTEX = 8;
    private static final int FLOATS_PER_QUAD_DATA = 4 * INTS_PER_VERTEX;

    private final TransmitterObjGeometry geometry;
    private final Function<String, TextureAtlasSprite> spriteLookup;

    public TransmitterQuadBaker(TransmitterObjGeometry geometry, Function<String, TextureAtlasSprite> spriteLookup) {
        this.geometry = geometry;
        this.spriteLookup = spriteLookup;
    }

    /** Bake every face of one group; rotation may be null. */
    public List<BakedQuad> bakeGroup(String group, Quaternionf rotation) {
        List<TransmitterObjGeometry.Face> faces = geometry.groups().get(group);
        if (faces == null) {
            throw new IllegalArgumentException("TransmitterQuadBaker: unknown group '" + group + "'");
        }
        List<BakedQuad> quads = new ArrayList<>(faces.size());
        for (TransmitterObjGeometry.Face face : faces) {
            TransmitterObjGeometry.Material material = geometry.material(face.material());
            if (material == null) {
                throw new IllegalArgumentException("TransmitterQuadBaker: face references unknown material '" + face.material() + "'");
            }
            TextureAtlasSprite sprite = spriteLookup.apply(material.textureAlias());
            if (sprite == null) {
                throw new IllegalStateException("TransmitterQuadBaker: no sprite for texture alias '" + material.textureAlias() + "'");
            }
            quads.add(bakeFace(face, sprite, material.tintIndex(), rotation));
        }
        return quads;
    }

    private BakedQuad bakeFace(TransmitterObjGeometry.Face face, TextureAtlasSprite sprite, int tintIndex, Quaternionf rotation) {
        List<TransmitterObjGeometry.FaceVertex> corners = face.vertices();
        int n = corners.size();
        float[][] pos = new float[n][3];
        float[][] uv = new float[n][2];
        Vector3f normal = faceNormal(corners);
        if (rotation != null) {
            // Rotate the face normal with the geometry so shading/direction stay consistent with the rotated part
            rotation.transform(normal);
        }
        for (int i = 0; i < n; i++) {
            TransmitterObjGeometry.FaceVertex c = corners.get(i);
            Vector3f v = new Vector3f(geometry.vertexX(c.vertex()), geometry.vertexY(c.vertex()), geometry.vertexZ(c.vertex()));
            if (rotation != null) {
                // Mekanism end-icon rotation pivots around the block center
                v.sub(0.5F, 0.5F, 0.5F);
                rotation.transform(v);
                v.add(0.5F, 0.5F, 0.5F);
            }
            pos[i][0] = v.x;
            pos[i][1] = v.y;
            pos[i][2] = v.z;
            float u = c.texCoord() >= 0 ? geometry.texU(c.texCoord()) : 0F;
            float w = c.texCoord() >= 0 ? geometry.texV(c.texCoord()) : 0F;
            // Pinned 1.21.1 TextureAtlasSprite#getU/getV interpolate the normalized 0..1 input directly
            uv[i][0] = sprite.getU(u);
            uv[i][1] = sprite.getV(w);
        }
        Direction direction = approxDirection(normal);
        int[] data = new int[FLOATS_PER_QUAD_DATA];
        int o = 0;
        for (int i = 0; i < n; i++) {
            data[o++] = Float.floatToRawIntBits(pos[i][0]);
            data[o++] = Float.floatToRawIntBits(pos[i][1]);
            data[o++] = Float.floatToRawIntBits(pos[i][2]);
            data[o++] = 0xFFFFFFFF; // color; tint applied by the renderer through tintIndex
            data[o++] = Float.floatToRawIntBits(uv[i][0]);
            data[o++] = Float.floatToRawIntBits(uv[i][1]);
            data[o++] = 0; // light (packed short pair) — block model shading fills this
            data[o++] = 0; // padding
        }
        // Triangulate anything that is not a quad through the same packed data via a sub-array is not possible in
        // vanilla BakedQuad, so non-quad faces must be split by the parser contract; enforce it here.
        if (n != 4) {
            throw new IllegalStateException("TransmitterQuadBaker: expected quad, got " + n + " corners");
        }
        return new BakedQuad(data, tintIndex, direction, sprite, false);
    }

    private Vector3f faceNormal(List<TransmitterObjGeometry.FaceVertex> corners) {
        // Prefer declared normals (all shipped faces reference one normal per quad); fall back to geometric normal.
        int first = corners.get(0).normal();
        if (first >= 0) {
            return new Vector3f(geometry.normalX(first), geometry.normalY(first), geometry.normalZ(first));
        }
        Vector3f a = new Vector3f(geometry.vertexX(corners.get(0).vertex()), geometry.vertexY(corners.get(0).vertex()), geometry.vertexZ(corners.get(0).vertex()));
        Vector3f b = new Vector3f(geometry.vertexX(corners.get(1).vertex()), geometry.vertexY(corners.get(1).vertex()), geometry.vertexZ(corners.get(1).vertex()));
        Vector3f c = new Vector3f(geometry.vertexX(corners.get(2).vertex()), geometry.vertexY(corners.get(2).vertex()), geometry.vertexZ(corners.get(2).vertex()));
        return b.sub(a).cross(c.sub(a)).normalize();
    }

    private static Direction approxDirection(Vector3f normal) {
        if (!Mth.equal(normal.length(), 1F)) {
            normal.normalize();
        }
        return Direction.getNearest(Mth.floor(normal.x * 1000F), Mth.floor(normal.y * 1000F), Mth.floor(normal.z * 1000F));
    }
}
