# Fabric bootstrap build

This is an independent Gradle/Loom build for the first Java Fabric migration
stage. It compiles explicit, reviewed allowlists from `fabric/src/main`,
`../src/api/java`, and `../src/main/java`; all other upstream Java sources remain
excluded. It uses Minecraft 1.21.1, Java 21, and Mojang official mappings.

## Audited source slices

`api-slice.txt` is the auditable allowlist of API source paths, relative to
`src/api/java`. The Fabric source set reads that file directly, so the listed
sources retain their original `mekanism.api.*` names without being copied or
modified. The allowlist now contains **27 source files**. The portable second
slice added the vanilla-only `AutomationType` and `Chunk3D` value types,
`SupportsColorMap`, and the complete four-file alloy/base tier closure
(`ITier`, `IAlloyTier`, `BaseTier`, and `AlloyTier`). The first intentional
platform adaptation adds `Action` while removing only its two NeoForge
`FluidAction` conversion methods. The first NBT slice adds the Mekanism-owned
registry-aware `INBTSerializable` contract and ports `DataHandlerUtils` without
changing its NBT layout or index behavior. See `../docs/API_DELTAS.md` for both
adaptations. These sources use only JDK, Minecraft, the existing annotation
slice, and one another. JetBrains annotations and JSR-305 remain `compileOnly`
dependencies and are not bundled in the mod.

`common-slice.txt` currently admits the five-file official GameEvent registration
closure plus nine startup-config/cache sources. `platform-slice.txt` admits only
the Fabric bootstrap and the registry/config adapters. The registry slice is
described in `../docs/FABRIC_REGISTRY.md`; the TOML-backed startup slice is
described in `../docs/FABRIC_CONFIG.md`. Night Config core/toml 3.8.3 are nested
inside the remapped mod JAR for config persistence.

The next nearby API candidates are blocked by deliberately excluded boundaries:
incremental-enum helpers close over `ConstantPredicates` (which imports
NeoForge fluid/tri-predicate and chemical types), and the text/provider enums
close over NeoForge `TranslatableEnum` or larger API text graphs. Those families
remain outside the current slice.

To repeat the portability audit and fail on a missing source, duplicate path,
or any `net.neoforged` reference, run from the repository root:

```bash
./gradlew -p fabric verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge verifyPlatformSliceNoNeoForge
```

The check is also a dependency of Java compilation. Blank lines and lines
beginning with `#` are ignored; keep actual source entries one per line.

Build from the repository root:

```bash
./fabric-build.sh
```

The remapped mod JAR is written to `fabric/build/libs/`. It contains the
Fabric bootstrap and every class compiled from the allowlisted API sources.
The repository-level `./build.sh [--clean] [--out <dir>]` contract invokes this
Fabric build and collects only its remapped release JAR. The exact buildable
NeoForge oracle remains available at baseline commit `4646f509`; current ported
sources are not expected to compile with the root NeoForge Gradle build.

Dependency versions were checked against Fabric's official Maven metadata:

- `https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml`
- `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`
- `https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml`
