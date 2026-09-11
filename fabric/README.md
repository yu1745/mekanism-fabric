# Fabric bootstrap build

This is an independent Gradle/Loom build for the first Java Fabric migration
stage. It compiles only `fabric/src/main`; the upstream Java sources in the
repository root remain the NeoForge baseline and are intentionally excluded.
It uses Minecraft 1.21.1, Java 21, and Mojang official mappings.

Build from the repository root:

```bash
./fabric-build.sh
```

The remapped mod JAR is written to `fabric/build/libs/`. The existing
`./build.sh` contract is unchanged and continues to build the NeoForge
baseline.

Dependency versions were checked against Fabric's official Maven metadata:

- `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml`
- `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`
- `https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml`
