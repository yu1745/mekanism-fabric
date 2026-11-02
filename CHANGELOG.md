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
- Of the optional third-party integrations upstream ships, four client-side viewers are supported and the rest
  are not: JEI, EMI, Jade and WTHIT (details below). REI, TOP, CraftTweaker, ProjectE and Curios are absent;
  Accessories is used for the equipment slots that Curios covers upstream.

- Iris shaders could not be validated in the named-mapping development client (W-98: mixin remapping failed).
  This records a development-environment limitation; release-client shader compatibility remains unverified.

### Optional integrations

All four are **compile-only** dependencies: the release JARs contain no code or assets from them, and installing
none of them changes nothing about a normal game.

| Viewer | Compiled against | Switch for a dev client | What it adds |
|---|---|---|---|
| JEI | `mezz.jei:jei-1.21.1-fabric:19.43.0.398` | `-PjeiDev` | 24 machine categories, item/chemical/fluid ingredients, transfer buttons |
| EMI | `maven.modrinth:emi:1.1.24+1.21.1+fabric` | `-PemiDev` | 30 categories, 65 chemicals, fluid and chemical ingredients |
| Jade | `maven.modrinth:jade:15.10.6+fabric` | `-PjadeDev` | block/entity tooltips with energy, chemical and fluid rows |
| WTHIT | `mcp.mobius.waila:wthit:fabric-12.10.2` | `-PwthitDev` | the same readings through WTHIT's plugin format |

Verified in a running client: JEI categories and recipe pages, EMI's chemical and fluid ingredients, Jade's
energy and fluid tooltip rows with real values, and WTHIT's energy row on an energised machine. Not verified for
any of them: multi-block and transmitter readings, the Robit entity tooltip, JEI's QIO recipe transfer, and the
ghost-ingredient drag path. WTHIT must be paired with `badpackets`, and both Jade and WTHIT are CC-BY-NC-SA-4.0 —
see [`docs/LICENSES.md`](docs/LICENSES.md).
- Configuration keys and restart metadata are retained. Files reload through their real owners; SERVER files default
  to `config/Mekanism/`, with existing per-file world overrides and `defaultconfigs` templates supported.

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
./gradlew -p fabric test      # 457 JVM tests; integration tasks per module when that module changes
```

The claims above were verified during the port.

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
