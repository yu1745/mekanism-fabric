# Fabric bootstrap build

This is an independent Gradle/Loom build for the first Java Fabric migration
stage. It compiles `fabric/src/main` plus an explicit, reviewed slice of the
official API sources under `../src/api/java`; all other upstream Java sources
remain excluded. It uses Minecraft 1.21.1, Java 21, and Mojang official
mappings.

## Official API slice

`api-slice.txt` is the auditable allowlist of API source paths, relative to
`src/api/java`. The Fabric source set reads that file directly, so the listed
sources retain their original `mekanism.api.*` names without being copied or
modified. The first slice contains **17 source files** (the original candidate
summary said 18, but its itemized list contains 17); their dependency closure
requires no additional Mekanism source. JetBrains annotations and JSR-305 are
available as `compileOnly` dependencies and are not bundled in the mod.

To repeat the portability audit and fail if any allowlisted source imports
NeoForge, run from the repository root:

```bash
! rg -v '^(#|$)' fabric/api-slice.txt | xargs -I{} rg -n '^import net\.neoforged\.' src/api/java/{}
```

Blank lines and lines beginning with `#` are ignored by Gradle; keep actual
source entries one per line so the command above remains directly repeatable.

Build from the repository root:

```bash
./fabric-build.sh
```

The remapped mod JAR is written to `fabric/build/libs/`. It contains the
Fabric bootstrap and every class compiled from the allowlisted API sources.
The existing `./build.sh` contract is unchanged and continues to build the
NeoForge baseline.

Dependency versions were checked against Fabric's official Maven metadata:

- `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml`
- `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`
- `https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml`
