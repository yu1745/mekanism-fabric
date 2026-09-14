package mekanism.fabric.client.render.obj;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Narrow shipped-transmitter OBJ baker. BLOCK vertices contain position, diffuse ABGR, atlas UV,
 * packed light, and three signed normal bytes (not padding).
 *
 * ModelState rotation composes with the end-icon rotation in official order and pivots about block center.
 * Like pinned NeoForge 21.1.200 ObjModel.ModelMesh.addQuads, OBJ UVs remain authored even when
 * ModelState.isUvLocked() is true: that owner only consumes getRotation(), unlike vanilla cuboid FaceBakery.
 */
public final class TransmitterQuadBaker {

    private static final int INTS_PER_VERTEX = 8;
    private final TransmitterObjGeometry geometry;
    private final Function<String, TextureAtlasSprite> spriteLookup;
    private final ModelState modelState;

    public TransmitterQuadBaker(TransmitterObjGeometry geometry, Function<String, TextureAtlasSprite> spriteLookup, ModelState modelState) {
        this.geometry = geometry;
        this.spriteLookup = spriteLookup;
        this.modelState = modelState;
    }

    /** Bake one group's faces, composing optional end-icon rotation after the incoming model rotation. */
    public List<BakedQuad> bakeGroup(String group, Quaternionf rotation) {
        List<TransmitterObjGeometry.Face> faces = geometry.groups().get(group);
        if (faces == null) {
            throw new IllegalArgumentException("TransmitterQuadBaker: unknown group '" + group + "'");
        }
        Transformation transform = modelState.getRotation();
        if (rotation != null) {
            transform = transform.compose(new Transformation(null, rotation, null, null));
        }
        Matrix4f positions = new Matrix4f().translation(0.5F, 0.5F, 0.5F)
              .mul(transform.getMatrix()).translate(-0.5F, -0.5F, -0.5F);
        Matrix3f normals = positions.normal(new Matrix3f());
        List<BakedQuad> quads = new ArrayList<>(faces.size());
        for (TransmitterObjGeometry.Face face : faces) {
            TransmitterObjGeometry.Material material = geometry.material(face.material());
            if (material == null) {
                throw new IllegalArgumentException("TransmitterQuadBaker: unknown material '" + face.material() + "'");
            }
            TextureAtlasSprite sprite = spriteLookup.apply(material.textureAlias());
            if (sprite == null) {
                throw new IllegalStateException("TransmitterQuadBaker: no sprite for alias '" + material.textureAlias() + "'");
            }
            quads.add(bakeFace(face, sprite, material, positions, normals));
        }
        return quads;
    }

    private BakedQuad bakeFace(TransmitterObjGeometry.Face face, TextureAtlasSprite sprite,
          TransmitterObjGeometry.Material material, Matrix4f positions, Matrix3f normals) {
        List<TransmitterObjGeometry.FaceVertex> corners = face.vertices();
        if (corners.size() < 3 || corners.size() > 4) {
            throw new IllegalArgumentException("TransmitterQuadBaker: expected triangle or quad, got " + corners.size());
        }
        boolean recalculateNormals = corners.stream().anyMatch(c -> c.normal() < 0);
        Vector3f faceNormal = recalculateNormals ? faceNormal(corners) : null;
        int[] data = new int[4 * INTS_PER_VERTEX];
        Direction direction = null;
        for (int i = 0; i < 4; i++) {
            // Official OBJ triangle representation duplicates its final corner in the fourth quad vertex.
            var corner = corners.get(Math.min(i, corners.size() - 1));
            Vector3f position = vertex(corner.vertex());
            positions.transformPosition(position);
            Vector3f normal = recalculateNormals ? new Vector3f(faceNormal)
                  : new Vector3f(geometry.normalX(corner.normal()), geometry.normalY(corner.normal()), geometry.normalZ(corner.normal()));
            normals.transform(normal).normalize();
            if (!normal.isFinite()) {
                throw new IllegalArgumentException("TransmitterQuadBaker: degenerate normal in " + face.material());
            }
            if (i == 0) {
                direction = Direction.getNearest(normal.x, normal.y, normal.z);
            }
            // OBJ loader's default UV quad, used only for a face without texture coordinates.
            float u = corner.texCoord() < 0 ? (i < 2 ? 0 : 1) : geometry.texU(corner.texCoord());
            float v = corner.texCoord() < 0 ? (i == 0 || i == 3 ? 0 : 1) : geometry.texV(corner.texCoord());
            int offset = i * INTS_PER_VERTEX;
            data[offset] = Float.floatToRawIntBits(position.x);
            data[offset + 1] = Float.floatToRawIntBits(position.y);
            data[offset + 2] = Float.floatToRawIntBits(position.z);
            data[offset + 3] = material.diffuseColor();
            data[offset + 4] = Float.floatToRawIntBits(sprite.getU(u));
            data[offset + 5] = Float.floatToRawIntBits(sprite.getV(v));
            data[offset + 6] = material.ambientLight() << 4 | material.ambientLight() << 20;
            data[offset + 7] = normalByte(normal.x) | normalByte(normal.y) << 8 | normalByte(normal.z) << 16;
        }
        // Shipped loader defaults shade_quads=true/emissive_ambient=true; all shipped Ka values are zero.
        return new BakedQuad(data, material.tintIndex(), direction, sprite, material.ambientLight() == 0);
    }

    private static int normalByte(float component) {
        return (byte) (Math.clamp(component, -1F, 1F) * 127F) & 255;
    }

    private Vector3f vertex(int index) {
        return new Vector3f(geometry.vertexX(index), geometry.vertexY(index), geometry.vertexZ(index));
    }

    private Vector3f faceNormal(List<TransmitterObjGeometry.FaceVertex> corners) {
        Vector3f origin = vertex(corners.get(0).vertex());
        return vertex(corners.get(1).vertex()).sub(origin).cross(vertex(corners.get(2).vertex()).sub(origin)).normalize();
    }
}
