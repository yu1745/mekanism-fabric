# Installing the Mekanism Fabric port

This is the **Fabric** build of Mekanism for Minecraft 1.21.1, ported from the official NeoForge
`v1.21.1-10.7.19.85`. It remains a development port — see [Limitations](#limitations) — and the release
server path and the data/save model are exercised by the gates listed in
[`docs/milestones/README.md`](milestones/README.md).

## Requirements

| Component | Version | Notes |
|---|---|---|
| Minecraft | 1.21.1 | exactly, no other versions are built |
| Java | 21+ | required by Minecraft 1.21.1 |
| Fabric Loader | >= 0.19.5 | declared by the core JAR |
| Fabric API | >= 0.116.17 | includes the registry/network APIs Mekanism uses |
| Team Reborn Energy | >= 4.1.0 | Fabric's energy API; Mekanism's cables and energy cubes speak it |

## The four JARs

| JAR | Mod id | Required? |
|---|---|---|
| `Mekanism-Fabric-Bootstrap-10.7.19.85+fabric.1.jar` | `mekanism` | **yes** — core machines, chemicals, tools framework |
| `MekanismTools-10.7.19.85+fabric.1-fabric.jar` | `mekanismtools` | optional — paxels, osmium/bronze/steel/refined obsidian gear |
| `MekanismAdditions-10.7.19.85+fabric.1-fabric.jar` | `mekanismadditions` | optional — plastic blocks, balloons, glow panels, glider |
| `MekanismGenerators-10.7.19.85+fabric.1-fabric.jar` | `mekanismgenerators` | optional — solar/heat/gas/bio/wind, fission and fusion |

The three addons declare `mekanism: 10.7.19.85+fabric.1` as an exact dependency, so keep all four JARs from the same
build. The version scheme is upstream's number plus a port build counter: `10.7.19.85+fabric.1` is the first Fabric
build of upstream `10.7.19.85`.

## Installing

1. Install Fabric Loader for Minecraft 1.21.1.
2. Put `fabric-api-*.jar` (>= 0.116.17) and `energy-*.jar` (Team Reborn Energy >= 4.1.0) into `mods/`.
3. Put the core JAR and whichever addons you want into `mods/`.
4. Clients and the server must use the same set of Mekanism JARs; configuration synchronization is version-checked
   and a mismatch is rejected at the configuration phase (this is intentional, see the configuration-rejection gate).

The server does not need any extra file: boot it with the Fabric server launcher and the same `mods/` contents.

## Configuration

Files default to the instance's `config/Mekanism/` directory. An existing file in
`<world>/serverconfig/Mekanism/` overrides that individual SERVER file; other files keep using the global directory.
Missing files can initialize from `defaultconfigs/Mekanism/` templates. Files are watched automatically, with each
value's original restart requirement retained: ordinary values reload live, WORLD values await the next world load,
and GAME values await a new process. Pending restart values survive later saves. Remote clients receive configuration
on connection; reload does not add a live PLAY configuration broadcast.

## Save compatibility with the NeoForge build

A small world authored by the official NeoForge build was loaded by this port and compared field by field:

- verified: machine block-entity NBT (metallurgic infuser, energy cube, quantum entangloporter), transmitter
  connection masks, and a formed multi-block (dynamic tank) — see
  `evidence/p8-release/w65-neoforge-save-probe.json`;
- verified: machine side configuration (`component_config`, eject flags) written by the official build and read back
  by the port — `w77-runtime-content.json`;
- verified in a later sample: machine inventory/side configuration and stored energy 5000 J transferred from the
  official build — `w80-save-roundtrip.json`;
- **not verified**: complete frequency/channel networks or arbitrary world transfers. Treat world transfer from
  NeoForge as experimental and keep a backup.

## Optional viewer integrations

Four optional client-side viewers are supported and compile-only: JEI, EMI, Jade and WTHIT. Drop the
  matching mod JARs into `mods/` and the integration activates; without them nothing changes. REI, TOP and the
  CraftTweaker/ProjectE data shipped by upstream are absent by design
  ([boundary page](milestones/M-integration.md)).

Each one is compiled against but never bundled, so the release JARs stay free of third-party code.

| Viewer | Version compiled against | Notes |
|---|---|---|
| JEI | `jei-1.21.1-fabric-19.43.0.398` | recipe categories plus item/chemical/fluid ingredients |
| EMI | `emi-1.1.24+1.21.1+fabric` | EMI needs Fabric Language Kotlin installed alongside it |
| Jade | `Jade-1.21.1-Fabric-15.10.6` | block and entity tooltips |
| WTHIT | `wthit-fabric-12.10.2` | requires `badpackets` (0.8.x) as well |

Versions matter: newer JEI builds are assembled with a newer Loom than this project uses and the loader rejects
them, and WTHIT is published on bai's maven rather than Modrinth's. For development the same set is available
through the `-PjeiDev`, `-PemiDev`, `-PjadeDev` and `-PwthitDev` Gradle switches instead of copying JARs.

## Limitations

- Large frequency/channel or transmission networks and long-run performance have no measured baseline.
- The release-JAR client path has one-off evidence only; it is not re-verified automatically with each build, and a
  clients must use mdb owned sessions. The retired standalone release-client launcher must not be used.
- Additions original16kHz/big-endian microphone capture and two-way voice are unverified; the earlier48kHz
  little-endian capture probe does not establish support for the actual protocol.
- MekaSuit HUD/keybind details and the controlled fission meltdown have not been read in a running game.
- **Iris shaders are not usable in the development client** (W-98): with Iris 1.8.14-beta.1 plus a shaderpack the
  Gradle client fails during mixin application (`InvalidMixinException: Unexpected NullPointerException parsing
  `@ModifyArg` annotation on iris$writeR…` → `MixinRemapper.unmapDesc`), because Iris ships mixins written against
  production mappings while the dev client runs named mappings. This is an Iris-in-development limitation, not a
  Mekanism defect; Sodium alone works and Mekanism renders correctly with it.
- **Third-party energy interop is verified against Tech Reborn only** (W-99), and only on the server side; other
  Team Reborn Energy consumers have not been sampled.

## Building from source

```bash
./build.sh --clean          # four release JARs into dist/ + build-info.json
scripts/release-check.sh    # server-side gates (clean build, static, packaged, paired, rejection, machine, Tech Reborn)
```

