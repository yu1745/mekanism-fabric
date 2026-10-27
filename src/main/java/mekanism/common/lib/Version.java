package mekanism.common.lib;

/**
 * Version v2.0.0. Simple version handling for Mekanism.
 *
 * @param major Major number for version
 * @param minor Minor number for version
 * @param build Build number for version
 *
 * @author AidanBrady
 */
public record Version(int major, int minor, int build) implements Comparable<Version> {

    //Fabric port delta: the maven-artifact dependency is absent from the fabric classpath (neoforge bundled it);
    //the artifact constructor parses the equivalent "major.minor.incremental" string instead. The excluded
    //entrypoint was its only caller.
    public Version(String artifactVersion) {
        this(java.util.Arrays.stream(artifactVersion.split("\\.")).mapToInt(Integer::parseInt).toArray());
    }

    private Version(int[] digits) {
        this(digits[0], digits[1], digits[2]);
    }

    //Fabric port delta: the ModContainer convenience constructor dropped with its loader type; the excluded
    //entrypoint was its only caller.
    /**
     * Gets a version object from a string.
     *
     * @param s - string object
     *
     * @return version if applicable, otherwise null
     */
    public static Version get(String s) {
        String[] split = s.replace('.', ':').split(":");
        if (split.length != 3) {
            return null;
        }

        int[] digits = new int[3];
        for (int i = 0; i < digits.length; i++) {
            try {
                digits[i] = Integer.parseInt(split[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new Version(digits[0], digits[1], digits[2]);
    }

    @Override
    public int compareTo(Version version) {
        if (version.major > major) {
            return -1;
        } else if (version.major == major) {
            if (version.minor > minor) {
                return -1;
            } else if (version.minor == minor) {
                return Integer.compare(build, version.build);
            }
        }
        return 1;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + build;
    }
}