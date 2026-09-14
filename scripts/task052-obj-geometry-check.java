import mekanism.fabric.client.render.obj.TransmitterObjGeometry;
import java.nio.file.*;
import java.util.*;

/** Focused check: run TransmitterObjGeometry against the real shipped transmitter assets. Plain javac/java, no MC. */
public class TransmitterObjGeometryCheck {
    public static void main(String[] args) throws Exception {
        String base = args[0];
        int failures = 0;
        failures += check("small", base + "transmitter_small.obj.mek", Map.of("transmitter_small.mtl", Files.readString(Path.of(base + "transmitter_small.mtl"))));
        Map<String, String> largeLibs = new HashMap<>();
        largeLibs.put("transmitter_large.mtl", Files.readString(Path.of(base + "transmitter_large.mtl")));
        largeLibs.put("transmitter_glass_large.mtl", Files.readString(Path.of(base + "transmitter_glass_large.mtl")));
        failures += check("large", base + "transmitter_large.obj.mek", largeLibs);
        Map<String, String> glassLibs = Map.of("transmitter_large.mtl", Files.readString(Path.of(base + "transmitter_glass_large.mtl"))); // mtl_override replaces the declared mtllib
        failures += checkGlass(base + "transmitter_large.obj.mek", glassLibs);
        if (failures == 0) {
            System.out.println("TRANSMITTER_OBJ_GEOMETRY_CHECK PASS");
        } else {
            System.out.println("TRANSMITTER_OBJ_GEOMETRY_CHECK FAILURES=" + failures);
            System.exit(1);
        }
    }

    static int check(String tag, String obj, Map<String, String> libs) throws Exception {
        int f = 0;
        var g = TransmitterObjGeometry.parse(Files.readString(Path.of(obj)), libs, true);
        String[] sides = {"down", "up", "north", "south", "west", "east"};
        String[] types = {"NONE", "NORMAL", "PUSH", "PULL"};
        Set<String> expected = new LinkedHashSet<>();
        for (String s : sides) for (String t : types) expected.add(s + t);
        if (!g.groups().keySet().equals(expected)) {
            f++;
            System.out.println(tag + ": group mismatch missing=" + minus(expected, g.groups().keySet()) + " extra=" + minus(g.groups().keySet(), expected));
        }
        int faces = 0, quads = 0, tris = 0;
        for (var e : g.groups().entrySet()) {
            for (TransmitterObjGeometry.Face face : e.getValue()) {
                faces++;
                if (face.vertices().size() == 4) quads++; else tris++;
                var mat = g.material(face.material());
                if (mat == null) { f++; System.out.println(tag + ": face with unknown material " + face.material()); }
                for (var c : face.vertices()) {
                    if (c.vertex() < 0 || c.vertex() >= g.vertexCount()) { f++; }
                    if (c.texCoord() >= 0 && c.texCoord() >= g.texCoordCount()) { f++; }
                    if (c.normal() >= 0 && c.normal() >= g.normalCount()) { f++; }
                }
            }
        }
        System.out.println(tag + ": groups=" + g.groups().size() + " faces=" + faces + " quads=" + quads + " tris=" + tris
            + " verts=" + g.vertexCount() + " uvs=" + g.texCoordCount() + " normals=" + g.normalCount() + " materials=" + g.materials().size());
        // flip_v sanity: raw first vt of small is "1.000000 0.000000"; flipped v must be 1.0
        if (tag.equals("small") && Math.abs(g.texV(0) - 1F) > 1e-6) { f++; System.out.println("small: flip_v not applied to first vt"); }
        return f;
    }

    static int checkGlass(String obj, Map<String, String> libs) throws Exception {
        int f = 0;
        var g = TransmitterObjGeometry.parse(Files.readString(Path.of(obj)), libs, true);
        int tinted = 0;
        for (var m : g.materials().values()) {
            if (m.textureAlias().endsWith("_glass") || m.textureAlias().contains("glass")) {
                if (m.tintIndex() != 1) { f++; System.out.println("glass material without tintIndex 1: " + m); }
                tinted++;
            }
        }
        if (tinted == 0) { f++; System.out.println("glass override MTL produced no glass materials"); }
        System.out.println("glass: tinted materials=" + tinted);
        return f;
    }

    static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> r = new LinkedHashSet<>(a); r.removeAll(b); return r;
    }
}
