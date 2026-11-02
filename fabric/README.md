# Fabric build

Independent Gradle/Loom build of the Fabric port (Minecraft 1.21.1, Java 21, Mojang official mappings).

- `src/main` — Fabric platform layer (registration, networking, configuration, storage adapters, mixins) in the
  `mekanism.fabric.*` packages and a few `mekanism.*` replacements for NeoForge-only classes.
- `src/{tools,additions,generators}` — per-module Fabric resources and entrypoints.
- `*-slice.txt` — allowlists of the official sources compiled from the repository root `src/` (names unchanged,
  files are not copied). `api-slice`, `common-slice` and `platform-slice` belong to the core; `tools-`, `additions-`
  and `generators-` slices to the modules.
- `src/*Fixture`, `src/*Test` — isolated runtime fixtures and integration tests; they never enter release JARs.

Build with `../build.sh`. See `../docs/Development.md` and `../docs/API_DELTAS.md`.
