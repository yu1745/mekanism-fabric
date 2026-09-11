# Fabric bootstrap build

This is an independent Gradle/Loom build for the first Java Fabric migration
stage. It compiles explicit, reviewed allowlists from `fabric/src/main`,
`../src/api/java`, and `../src/main/java`; all other upstream Java sources remain
excluded. It uses Minecraft 1.21.1, Java 21, and Mojang official mappings.

## Audited source slices

`api-slice.txt` is the auditable allowlist of API source paths, relative to
`src/api/java`. The Fabric source set reads that file directly, so the listed
sources retain their original `mekanism.api.*` names without being copied or
modified. The allowlist contains **35 current source files**: **34 unchanged official
baseline files** plus the project-owned registry-aware `INBTSerializable` NBT
contract. Intentional API adaptations remain limited to that contract and
`Action`'s removal of two NeoForge `FluidAction` conversion methods; see
`../docs/API_DELTAS.md`. JetBrains annotations and JSR-305 remain `compileOnly`
and are not bundled in the mod.

`common-slice.txt` contains **23 official source files**, and
`platform-slice.txt` contains **10 project-owned Fabric sources**. Together they
form bounded registry/GameEvent, TOML startup-config, configuration-handshake,
internal energy storage/holder, and internal heat storage/holder closures. Heat
includes the unchanged dynamic `VariableHeatCapacitor`; neither energy nor heat
claims external API, transaction, configurable-holder, network, or machine-owner
integration. See `../docs/FABRIC_REGISTRY.md`, `../docs/FABRIC_CONFIG.md`,
`../docs/FABRIC_NETWORK.md`, `../docs/FABRIC_ENERGY.md`, and
`../docs/FABRIC_HEAT.md`. Night Config core/toml 3.8.3 are nested in the remapped
mod JAR.

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
