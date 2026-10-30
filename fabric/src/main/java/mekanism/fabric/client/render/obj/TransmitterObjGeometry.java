package mekanism.fabric.client.render.obj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Narrow, transmitter-specific parser for Mekanism's shipped {@code transmitter_{small,large}.obj.mek} geometry and
 * the accompanying MTL libraries ({@code transmitter_small.mtl}, {@code transmitter_large.mtl},
 * {@code transmitter_glass_large.mtl}).
 *
 * <p>This is not a general-purpose OBJ implementation: it supports exactly the constructs the shipped assets use
 * ({@code v}/{@code vt}/{@code vn}, {@code o} groups (24 = 6 sides x 4 connection types), {@code usemtl},
 * {@code mtllib}, quads/triangles in {@code v}, {@code v/vt}, {@code v/vt/vn}, {@code v//vn} forms, negative
 * indices, {@code newmtl}, {@code map_Kd #alias} or {@code map_Kd <namespace:textures/id>}, {@code forge_TintIndex}) and fails loudly on anything else so
 * malformed input can never silently drop geometry.</p>
 *
 * <p>Pure Java, no Minecraft dependencies, so it can be checked directly against the real assets without a client
 * runtime.</p>
 */
public final class TransmitterObjGeometry {

    /** One face corner; indices are 0-based; {@code -1} marks an absent texCoord or normal. */
    public record FaceVertex(int vertex, int texCoord, int normal) {
    }

    /** Shipped MTL alias, tint index, diffuse ABGR (Kd/d), and emissive ambient level (Ka). */
    public record Material(String textureAlias, int tintIndex, int diffuseColor, int ambientLight) {
    }

    /**
     * A face belongs to exactly one group and one material at parse time. The record defensively copies its corner
     * list: the parser builds each list as a mutable {@link ArrayList}, and wrapping only the enclosing group list
     * would still expose that mutable backing list to every caller holding a face reference.
     */
    public record Face(String material, List<FaceVertex> vertices) {

        public Face {
            vertices = List.copyOf(vertices);
        }
    }

    private final float[] vertices;
    private final float[] texCoords;
    private final float[] normals;
    private final Map<String, List<Face>> groups;
    private final Map<String, Material> materials;

    private TransmitterObjGeometry(float[] vertices, float[] texCoords, float[] normals, Map<String, List<Face>> groups, Map<String, Material> materials) {
        this.vertices = vertices;
        this.texCoords = texCoords;
        this.normals = normals;
        //Deep immutability: both maps and every group's face list are unmodifiable, and FaceVertex/Face records
        //carry no mutable state, so published geometry can never be mutated after parse.
        Map<String, List<Face>> groupView = new LinkedHashMap<>();
        for (Map.Entry<String, List<Face>> e : groups.entrySet()) {
            groupView.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        this.groups = Collections.unmodifiableMap(groupView);
        this.materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
    }

    public int vertexCount() {
        return vertices.length / 3;
    }

    public int texCoordCount() {
        return texCoords.length / 2;
    }

    public int normalCount() {
        return normals.length / 3;
    }

    public float vertexX(int i) {
        return vertices[i * 3];
    }

    public float vertexY(int i) {
        return vertices[i * 3 + 1];
    }

    public float vertexZ(int i) {
        return vertices[i * 3 + 2];
    }

    /** TexCoord U in OBJ space (flip already applied if requested at parse time). */
    public float texU(int i) {
        return texCoords[i * 2];
    }

    public float texV(int i) {
        return texCoords[i * 2 + 1];
    }

    public float normalX(int i) {
        return normals[i * 3];
    }

    public float normalY(int i) {
        return normals[i * 3 + 1];
    }

    public float normalZ(int i) {
        return normals[i * 3 + 2];
    }

    /** Ordered group ({@code o}/{@code g}) name to face list. */
    public Map<String, List<Face>> groups() {
        return groups;
    }

    /** Material name to material. */
    public Map<String, Material> materials() {
        return materials;
    }

    @Nullable
    public Material material(String name) {
        return materials.get(name);
    }

    public static TransmitterObjGeometry parse(String objSource, Map<String, String> mtlSources, boolean flipV) {
        return new Parser(objSource, mtlSources, flipV).parse();
    }

    private static final class Parser {
        private final String objSource;
        private final Map<String, String> mtlSources;
        private final boolean flipV;
        private final List<Float> vertices = new ArrayList<>();
        private final List<Float> texCoords = new ArrayList<>();
        private final List<Float> normals = new ArrayList<>();
        private final Map<String, List<Face>> groups = new LinkedHashMap<>();
        private final Map<String, Material> materials = new LinkedHashMap<>();
        private String currentGroup;
        private String currentMaterial;

        Parser(String objSource, Map<String, String> mtlSources, boolean flipV) {
            this.objSource = objSource;
            this.mtlSources = mtlSources;
            this.flipV = flipV;
        }

        TransmitterObjGeometry parse() {
            int lineNo = 0;
            for (String raw : objSource.split("\n", -1)) {
                lineNo++;
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> expect(lineNo, parts, 4, "v x y z")
                            .run(() -> {
                                vertices.add(Float.parseFloat(parts[1]));
                                vertices.add(Float.parseFloat(parts[2]));
                                vertices.add(Float.parseFloat(parts[3]));
                            });
                    case "vt" -> expect(lineNo, parts, 3, "vt u v")
                            .run(() -> {
                                float u = Float.parseFloat(parts[1]);
                                float v = Float.parseFloat(parts[2]);
                                if (flipV) {
                                    v = 1F - v;
                                }
                                texCoords.add(u);
                                texCoords.add(v);
                            });
                    case "vn" -> expect(lineNo, parts, 4, "vn x y z")
                            .run(() -> {
                                normals.add(Float.parseFloat(parts[1]));
                                normals.add(Float.parseFloat(parts[2]));
                                normals.add(Float.parseFloat(parts[3]));
                            });
                    case "o", "g" -> expect(lineNo, parts, 2, parts[0] + " name")
                            .run(() -> {
                                currentGroup = parts[1];
                                groups.putIfAbsent(currentGroup, new ArrayList<>());
                            });
                    case "usemtl" -> expect(lineNo, parts, 2, "usemtl name")
                            .run(() -> currentMaterial = parts[1]);
                    case "mtllib" -> {
                        for (int i = 1; i < parts.length; i++) {
                            parseMtl(mtlSources.get(parts[i]), parts[i]);
                        }
                    }
                    case "f" -> parseFace(lineNo, parts);
                    case "s" -> {
                        // shading groups are ignored (irrelevant for baked quads)
                    }
                    case "Ka", "Kd", "Ks", "Ns", "d", "Tr", "Ni", "illum", "Ke" -> {
                        // lighting/opacity metadata is irrelevant: Mekanism textures are pre-baked atlases
                    }
                    default -> throw new IllegalArgumentException("TransmitterObjGeometry: unsupported OBJ directive '" + parts[0] + "' at line " + lineNo
                          + " of " + parts.length + "-token line; this parser only supports the shipped transmitter assets");
                }
            }
            if (groups.isEmpty()) {
                throw new IllegalArgumentException("TransmitterObjGeometry: no groups (o/g) found; refusing empty geometry");
            }
            for (Map.Entry<String, List<Face>> e : groups.entrySet()) {
                if (e.getValue().isEmpty()) {
                    throw new IllegalArgumentException("TransmitterObjGeometry: group '" + e.getKey() + "' has no faces");
                }
            }
            float[] v = toArray(vertices), t = toArray(texCoords), n = toArray(normals);
            return new TransmitterObjGeometry(v, t, n, groups, materials);
        }

        private void parseFace(int lineNo, String[] parts) {
            expect(lineNo, parts, 4, "f v ...");
            if (currentGroup == null) {
                throw new IllegalArgumentException("TransmitterObjGeometry: face at line " + lineNo + " before any o/g group");
            }
            if (currentMaterial == null) {
                throw new IllegalArgumentException("TransmitterObjGeometry: face at line " + lineNo + " before any usemtl");
            }
            if (parts.length - 1 > 4) {
                throw new IllegalArgumentException("TransmitterObjGeometry: face at line " + lineNo + " has " + (parts.length - 1)
                      + " vertices; only triangles and quads are supported");
            }
            List<FaceVertex> corners = new ArrayList<>(parts.length - 1);
            for (int i = 1; i < parts.length; i++) {
                String[] idx = parts[i].split("/");
                int vi = positiveIndex(idx[0], vertices.size() / 3, lineNo);
                int ti = -1;
                int ni = -1;
                switch (idx.length) {
                    case 1 -> {
                    }
                    case 2 -> ti = positiveIndex(idx[1], texCoords.size() / 2, lineNo);
                    case 3 -> {
                        if (!idx[1].isEmpty()) {
                            ti = positiveIndex(idx[1], texCoords.size() / 2, lineNo);
                        }
                        if (!idx[2].isEmpty()) {
                            ni = positiveIndex(idx[2], normals.size() / 3, lineNo);
                        }
                    }
                    default -> throw new IllegalArgumentException("TransmitterObjGeometry: bad face corner '" + parts[i] + "' at line " + lineNo);
                }
                corners.add(new FaceVertex(vi, ti, ni));
            }
            groups.get(currentGroup).add(new Face(currentMaterial, corners));
        }

        private int positiveIndex(String raw, int count, int lineNo) {
            int i = Integer.parseInt(raw);
            if (i > 0) {
                i--;
            } else if (i < 0) {
                i += count;
            }
            if (i < 0 || i >= count) {
                throw new IllegalArgumentException("TransmitterObjGeometry: index " + raw + " out of range (count=" + count + ") at line " + lineNo);
            }
            return i;
        }

        private void parseMtl(@Nullable String source, String libraryName) {
            if (source == null) {
                throw new IllegalArgumentException("TransmitterObjGeometry: mtllib '" + libraryName + "' was not provided to the parser");
            }
            String name = null;
            String alias = null;
            int tint = -1;
            float red = 1, green = 1, blue = 1, alpha = 1;
            int ambientLight = 0;
            int lineNo = 0;
            for (String raw : source.split("\n", -1)) {
                lineNo++;
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "newmtl" -> {
                        if (name != null) {
                            materials.put(name, buildMaterial(name, alias, tint, libraryName, red, green, blue, alpha, ambientLight));
                        }
                        if (parts.length != 2) {
                            throw new IllegalArgumentException("TransmitterObjGeometry: malformed newmtl in " + libraryName + " line " + lineNo);
                        }
                        name = parts[1];
                        alias = null;
                        tint = -1;
                        red = green = blue = alpha = 1;
                        ambientLight = 0;
                    }
                    case "Kd" -> {
                        if (parts.length != 4) {
                            throw new IllegalArgumentException("Malformed Kd in " + libraryName + " line " + lineNo);
                        }
                        red = Float.parseFloat(parts[1]);
                        green = Float.parseFloat(parts[2]);
                        blue = Float.parseFloat(parts[3]);
                    }
                    case "d" -> alpha = Float.parseFloat(parts[1]);
                    case "Ka" -> {
                        if (parts.length != 4) {
                            throw new IllegalArgumentException("Malformed Ka in " + libraryName + " line " + lineNo);
                        }
                        ambientLight = (int) ((Float.parseFloat(parts[1]) + Float.parseFloat(parts[2]) + Float.parseFloat(parts[3])) * 15 / 3F);
                    }
                    case "map_Kd" -> {
                        //Two shipped conventions: the transmitter MTLs reference the model JSON's texture map via
                        //'#alias', while the official NeoForge entity OBJs (Meka-Tool/Meka-Suit) carry the texture
                        //id directly. Both land in Material.textureAlias; the caller's sprite lookup decides how
                        //the name resolves.
                        if (parts.length != 2 || parts[1].isBlank()
                              || (!parts[1].startsWith("#") && !parts[1].contains(":"))) {
                            throw new IllegalArgumentException("TransmitterObjGeometry: map_Kd in " + libraryName + " line " + lineNo
                                  + " must reference a texture alias via '#name' or a namespaced texture id (got '" + (parts.length > 1 ? parts[1] : "") + "')");
                        }
                        alias = parts[1].startsWith("#") ? parts[1].substring(1) : parts[1];
                    }
                    case "forge_TintIndex" -> {
                        if (parts.length != 2) {
                            throw new IllegalArgumentException("TransmitterObjGeometry: malformed forge_TintIndex in " + libraryName + " line " + lineNo);
                        }
                        tint = Integer.parseInt(parts[1]);
                    }
                    default -> {
                        // Specular/illumination metadata has no baked-block equivalent in the shipped OBJ owner.
                    }
                }
            }
            if (name != null) {
                materials.put(name, buildMaterial(name, alias, tint, libraryName, red, green, blue, alpha, ambientLight));
            }
        }

        private Material buildMaterial(String name, String alias, int tint, String libraryName,
              float red, float green, float blue, float alpha, int ambientLight) {
            int rgba = channel(red) | channel(green) << 8 | channel(blue) << 16 | channel(alpha) << 24;
            //A material without map_Kd still owns its diffuse/tint/lighting values. The official contents
            //model deliberately has no texture: its bake supplies the network icon for every material.
            return new Material(alias == null ? "minecraft:missingno" : alias, tint, rgba, ambientLight);
        }

        private static int channel(float value) {
            return (int) (Math.clamp(value, 0F, 1F) * 255F);
        }

        private CheckedDirective expect(int lineNo, String[] parts, int min, String syntax) {
            return new CheckedDirective(parts.length >= min, lineNo, syntax);
        }

        private record CheckedDirective(boolean ok, int lineNo, String syntax) {
            void run(Runnable r) {
                if (!ok) {
                    throw new IllegalArgumentException("TransmitterObjGeometry: expected '" + syntax + "' at line " + lineNo);
                }
                r.run();
            }
        }
    }

    private static float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
