# Licenses and attribution

This repository is a **Fabric port** of the official Mekanism mod. It keeps upstream's package names, registry
ids, recipes, NBT and save semantics, so attribution follows the upstream project.

## Project license

**MIT**, see [`LICENSE`](../LICENSE): `Copyright (c) 2017-2025 Aidan C. Brady`.

Every release JAR carries a copy of that text as `LICENSE_<artifact>`:

| JAR | License entry |
|---|---|
| `Mekanism-Fabric-Bootstrap-<version>.jar` | `LICENSE_Mekanism-Fabric-Bootstrap` |
| `MekanismTools-<version>-fabric.jar` | `LICENSE_MekanismTools` |
| `MekanismAdditions-<version>-fabric.jar` | `LICENSE_MekanismAdditions` |
| `MekanismGenerators-<version>-fabric.jar` | `LICENSE_MekanismGenerators` |

Modifications by the Fabric port are distributed under the same MIT license; the original copyright notice is
kept in every file that came from upstream.

## Libraries bundled inside the JARs

Only the core JAR bundles code. The three addons bundle no libraries and no classes from other projects.

| Library | Version | License | Shipped license files |
|---|---|---|---|
| night-config `core` | 3.8.3 | LGPL-3.0 | `META-INF/licenses/night-config-LGPL-3.0.txt`, `night-config-NOTICE.txt` |
| night-config `toml` | 3.8.3 | LGPL-3.0 | same as above |
| Apache Maven `maven-artifact` | 3.9.9 | Apache-2.0 | `META-INF/licenses/maven-artifact-Apache-2.0.txt`, `maven-artifact-NOTICE.txt` |
| NeoForge license bundle | — | LGPL-2.1 | `META-INF/licenses/NeoForge-LGPL-2.1.txt`, `NeoForge-NOTICE.txt` |

Notes:

- `night-config` reads the Mekanism TOML configuration files.
- `maven-artifact` parses the artifact version strings the official code expects.
- The NeoForge license copies are inherited from the official build's resource bundle for attribution clarity.
  **No `net.neoforged.*` code is bundled** — the port re-implements the platform boundary on Fabric APIs, and the
  NeoForge classes are compile-time only.

## Declared runtime dependencies (not bundled)

| Component | License | Why it is needed |
|---|---|---|
| Minecraft 1.21.1 | Mojang EULA / proprietary | the game itself |
| Fabric Loader | Apache-2.0 | mod loader |
| Fabric API | Apache-2.0 | registry, networking, transfer and resource APIs |
| Team Reborn Energy | MIT | Fabric energy API used by cables and energy cubes |

These are installed by the user next to the JARs; they are declared in `fabric.mod.json` (`depends`) so the
loader reports a missing or too-old version instead of failing at runtime.

## Third-party content

The port does not bundle or ship any optional integration code: JEI/EMI/REI, Jade/WTHIT/TOP, CraftTweaker,
ProjectE, Curios and Accessories are absent (see
[`docs/milestones/M-integration.md`](milestones/M-integration.md)). Upstream data files that reference other
mods are either transformed mechanically (compat recipe conditions) or left out, and are covered by the
upstream MIT license.

## Where the evidence lives

- Version and metadata of the four JARs: [`docs/milestones/evidence/p8-release/w71-release-metadata.json`](milestones/evidence/p8-release/w71-release-metadata.json)
- Resource-level comparison against the official NeoForge JARs: [`docs/milestones/evidence/p8-release/w75-resource-inventory.json`](milestones/evidence/p8-release/w75-resource-inventory.json)
- This audit: [`docs/milestones/evidence/p8-release/w85-licenses.json`](milestones/evidence/p8-release/w85-licenses.json)
