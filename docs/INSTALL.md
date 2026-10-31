# Installing the Mekanism Fabric port

This is the **Fabric** build of Mekanism for Minecraft 1.21.1, ported from the official NeoForge
`v1.21.1-10.7.19.85`. It is not a complete playable port yet — see [Limitations](#limitations) — but the release
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

## Save compatibility with the NeoForge build

A small world authored by the official NeoForge build was loaded by this port and compared field by field:

- verified: machine block-entity NBT (metallurgic infuser, energy cube, quantum entangloporter), transmitter
  connection masks, and a formed multi-block (dynamic tank) — see
  [`evidence/p8-release/w65-neoforge-save-probe.json`](milestones/evidence/p8-release/w65-neoforge-save-probe.json);
- verified: machine side configuration (`component_config`, eject flags) written by the official build and read back
  by the port — [`w77-runtime-content.json`](milestones/evidence/p8-release/w77-runtime-content.json);
- **not verified**: machine inventories, stored energy, frequencies and channel assignments. Treat world transfer from
  NeoForge as experimental and keep a backup.

## Limitations

- No third-party integration yet: JEI/EMI/REI recipe viewers and Jade/WTHIT looking-at are not wired up
  ([boundary page](milestones/M-integration.md)). CraftTweaker scripts and ProjectE data shipped by upstream are
  absent by design.
- Frequency/channel networks, large transmission networks and long-run performance have no measured baseline.
- The release-JAR client path has one-off evidence only; it is not re-verified automatically with each build, and a
  version change requires a fresh approval to re-run it.
- MekaSuit HUD/keybind details and the controlled fission meltdown have not been read in a running game.

## Building from source

```bash
./build.sh --clean          # four release JARs into dist/ + build-info.json
scripts/release-check.sh    # server-side release gates (clean build, static, packaged, paired, rejection, machine)
```

Evidence for every verification lives under [`docs/milestones/evidence/`](milestones/evidence/).
