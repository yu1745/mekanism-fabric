# Development

Mekanism Fabric is built for Minecraft 1.21.1 with Java 21, Gradle 9 and Fabric Loom, using Mojang official mappings.

## Layout

* `/src` — the official Mekanism sources (`api`, `main`, and the `additions`, `generators`, `tools` modules). They are
  compiled from explicit allowlists, not copied.
* `/fabric` — the Fabric build and the platform layer. `fabric/src/main` holds the Fabric-specific code; the root
  `src` sources are included through `fabric/*-slice.txt` (one path per line; `api-slice.txt`, `common-slice.txt`,
  `platform-slice.txt`, and per-module slices). Adding an official source file to the build means adding it to a slice.
* `/fabric/src/<module>` — Fabric resources for each module; `*Fixture` and `*Test` source sets are isolated test
  runtimes and never enter the release JARs.
* `/scripts` — smoke gates (`fabric-*-smoke.sh`) and `release-check.sh`.

## Build and test

```bash
./build.sh [--clean] [--out dist]   # four release JARs + build-info.json (sha256)
./fabric-build.sh                   # development build
./gradlew -p fabric test            # everyday JVM tests
./gradlew -p fabric verifyApiSliceNoNeoForge
```

Integration tasks that start a Minecraft runtime are run when the matching area changes: `fabricRegistryTest`,
`qioTransactionTest`, `fabricClientModelTest`, `fabricDataMapTest`, `fabricRecipeRegistryTest`, `fabricTransferTest`.
Before a release run `scripts/release-check.sh` (clean build, packaged server, paired network, configuration
rejection and machine smoke gates). Each `scripts/fabric-*-smoke.sh` documents what it checks in its header.

## Running the game

`./gradlew -p fabric runClient` starts a development client. Pick a module with `-PdevModule=core|tools|additions|generators`
(run directories are `fabric/run` and `fabric/run-<module>`). Optional recipe viewers and look-at mods can be added to the
dev runtime with `-PjeiDev`, `-PemiDev`, `-PjadeDev` and `-PwthitDev`.

`docs/Mekanism.xml` is the IntelliJ code style preset.
