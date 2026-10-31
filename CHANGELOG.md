# Changelog

## 10.7.19.85+fabric.1 — first Fabric build

The first release build of the Fabric port. The version keeps the upstream NeoForge number (`10.7.19.85`) and adds
`+fabric.1` for this port's build counter, so an installation can always be traced back to the official release it
was ported from.

### What works

- **Core** (`mekanism`): ore processing, energy cubes and cables, chemicals (gas/infusion/pigment/slurry), the
  metallurgic family of machines, factories, QIO, teleporters, dynamic tanks and the other multi-blocks, radiation,
  gear (MekaSuit/MekaTool), config sync and the full command set including `/mek build`.
- **Tools** (`mekanismtools`): paxels and the osmium/bronze/steel/refined obsidian tool and armour lines, with the
  official recipes and stats.
- **Additions** (`mekanismadditions`): plastic blocks and stairs, glow panels, balloons, baby mobs, the glider and TNT.
- **Generators** (`mekanismgenerators`): solar, heat, gas, bio, wind, advanced solar, fission and fusion reactors,
  the industrial turbine and the boiler integration.
- **Saves**: worlds authored by the official NeoForge build load with the same block entities, machine inventories,
  side configuration, stored energy, transmitter networks and formed multi-blocks (see the evidence links below).

### What is different from the NeoForge build

- Platform plumbing is Fabric: Fabric Loader/API plus Team Reborn Energy instead of NeoForge energy. Cables and
  energy cubes speak Team Reborn Energy, and the mod declares it as a required dependency.
- Compat recipe conditions are translated at build time (`neoforge:conditions` → `fabric:load_conditions`); the
  official sources are untouched.
- The optional third-party integrations upstream ships (JEI/EMI/REI viewers, Jade/WTHIT/TOP looking-at,
  CraftTweaker, ProjectE, Curios) are not part of this build. Accessories is used for the equipment slots that
  Curios covers upstream.
- `NeoForge`-branded config entries are gone; the four configuration files keep the same keys and values otherwise.

### Known limitations of this build

- No recipe-viewer or looking-at integration: EMI/JEI/REI and Jade/WTHIT/TOP are not wired up yet
  ([boundary page](docs/milestones/M-integration.md)).
- Large transmission networks, turbines and reactors have a first throughput baseline but no long-run soak yet.
- The release-JAR client path has one-off evidence; it is not re-verified automatically on every build.
- MekaSuit HUD values were only read on an uncharged suit (they render, at 0%); a controlled fission meltdown has
  not been triggered in game.
- Machine inventories, side configuration and stored energy round-trip from NeoForge saves; frequencies/channels
  and QIO contents were not part of that probe.

### Verifying a build

```bash
./build.sh --clean            # four JARs into dist/ + build-info.json
scripts/release-check.sh      # clean build, static gates, packaged server, paired network, rejection, machine smoke
scripts/compare-resource-inventory.py   # data/assets diff against the official NeoForge JARs
scripts/compare-registry-ids.py         # registry id inventory against the official JARs
./gradlew -p fabric test      # 451 JVM tests; integration tasks per module when that module changes
```

Evidence for each claim above lives under [`docs/milestones/evidence/`](docs/milestones/evidence/); the per-item
index is in [`docs/milestones/M-release.md`](docs/milestones/M-release.md).

## Release notes (ready to paste)

> **Mekanism for Fabric 1.21.1 — 10.7.19.85+fabric.1 (first build)**
>
> The official Mekanism 10.7.19.85 ported to Fabric: machines, ore processing, chemicals, QIO, multi-blocks,
> radiation, MekaSuit/MekaTool gear, plus the Tools, Additions and Generators addons.
>
> Requires Minecraft 1.21.1, Java 21, Fabric Loader 0.19.5+, Fabric API 0.116.17+ and Team Reborn Energy 4.1.0+.
> Install the core JAR plus whichever addons you want — the addons depend on the exact core version.
>
> Loads worlds made with the NeoForge build: machine inventories, side configuration, stored energy, transmitter
> networks and formed multi-blocks survive the move. This is still an early port: recipe viewers and looking-at
> mods are not integrated, and large-network/soak and a controlled fission meltdown have not been verified. Keep a
> backup when migrating a world.
