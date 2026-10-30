# Fabric Chemical vertical (Task 040)

## Status and boundary

Task 040 was implemented as candidate `dc9c5687`, hardened by focused corrective `ff7e5f7c`,
completed by parent assertion commit `0e719682`, and integrated at merge commit `f24ce94e` over the
Task 040 approval line (`5aadf581`, code baseline `1a65b0a8`). The integrated vertical compiles
API/common/platform slices **98/164/33** (295 production sources) and fixes the executable test
contract at ordinary **73 suites/355 tests**, transfer-Knot **4/21**, root-registry **1/2**, and
data-map-Knot **2/4**, aggregate **80/382**. The three jqwik properties remain at 100,000 tries.

This vertical owns the internal Mekanism Chemical domain needed before recipes and machines:

- one synchronized intrusive/defaulted `mekanism:chemical` registry;
- canonical `mekanism:empty`, the 32 official named chemicals, 18 pigments, and 14 slurries;
- `Chemical`, `ChemicalStack`, `BasicChemicalTank`, handlers, holders, proxy, and slot metadata;
- five authoritative chemical data maps and context-local legacy/modern attributes;
- complete selected tooltip/data-map services;
- `common.toml` plus a preserving world `general.toml` policy fragment;
- bounded configuration snapshot/ACK and play-phase reload publication;
- disconnect, stop, reconnect, and integrated owner isolation.

It does **not** add a Fabric chemical lookup/provider or transaction contract, environmental radiation
manager, chemical ingredients, configurable holder/ejector, machine, menu, block/item registration,
Crusher, full `MekanismAPI`, or module/Robit registries. Internal `Action.SIMULATE`/`EXECUTE` behavior is
preserved but is not advertised as external rollback-safe storage.

## Registry and canonical value ownership

`ChemicalRegistry` is the platform-neutral owner of the registry key, empty key, installed registry,
canonical empty value, and canonical empty `Holder.Reference`. `FabricChemicalRegistryLifecycle`
constructs a `FabricDefaultedRegistry<Chemical>`, registers that exact object in the vanilla root with
Fabric `MODDED` and `SYNCED`, registers empty once, then submits all official declarations through the
deferred owner. It verifies:

1. exact size 65 and default raw ID 0;
2. every expected name in declaration order has raw ID 1 through 64;
3. each registry lookup, value, deferred holder, and canonical holder retains exact identity;
4. the mutable-to-frozen transition occurs on the construction thread.

Raw IDs are verification-only. Fabric registry synchronization owns server/client remapping, while all
Task 040 application snapshots identify map keys and referenced coolant chemicals by resource name.
The registry is pre-frozen before publication. Vanilla `RegistryAccess` creation/reload may call
`freeze()` again from a reload worker; only the already-`FROZEN` idempotent path is cross-thread. Every
population/root/first-freeze operation remains owner-thread-only and late mutation fails.

## Chemical ingredient type registry ownership (Task052 continuation12)

The six built-in `ChemicalIngredient` codecs are stored in the single canonical vanilla registry
`mekanism:chemical_ingredient_type`, owned by `ChemicalIngredientTypeRegistry` (API seam) and constructed by
`FabricChemicalIngredientTypeRegistryLifecycle`. The construction owner allocates the plain, non-intrusive, non-defaulted
`MappedRegistry`, installs that exact instance into the API seam, publishes it into the vanilla root with Fabric `MODDED`+`SYNCED`
through the existing `FabricRegistryBuilder`, then drives `MekanismChemicalIngredientTypes` (six codecs in official order) and
freezes before recipe decoding. The native identity value map provides exact codec identity and `idOf`, the native id map rejects
duplicate IDs, an unknown ID throws `IllegalArgumentException` instead of silently defaulting, and duplicate codec identity, late
registration, and missing built-ins all fail. This is not a private registry: the same object registered in the root is what the
ingredient codecs consult. The plain (non-root) JVM frozen by `Bootstrap.bootStrap` cannot publish into the root, so ordinary/local
bootstraps install the same genuine native registry directly, while production and the loader-window integration worker go through
the construction owner.

`ChemicalStack` canonicalizes serializable reference wrappers by resource key to the exact registry
holder. It retains canonical `EMPTY`, required/fixed/optional/lenient codecs, registry-aware NBT,
amount-before-holder stream order, positive/non-empty validation, and true-`long` amounts. There is no
fluid factor-81 conversion in the Chemical domain.

## Data maps, attributes, and owner contexts

The former platform-only `FabricDataMapType` is removed. Public API now uses
`MekanismDataMapType<R,T>`, an identity descriptor containing registry key, ID, persistent codec, and
an optional `StreamCodec<RegistryFriendlyByteBuf,T>`; `synchronizedToClient()` is true exactly when
that codec exists. API/common code has no Fabric dependency.

`FabricChemicalDataMaps` supplies these synchronized descriptors as one catalog:

| Descriptor | Packaged baseline entries |
|---|---:|
| `chemical_solid_tag` | 7 clean slurries |
| `chemical_attribute_fuel` | 2 (`ethene`, `hydrogen`) |
| `chemical_attribute_radioactivity` | 4 nuclear chemicals |
| `chemical_attribute_cooled_coolant` | 1 (`sodium`) |
| `chemical_attribute_heated_coolant` | 1 (`superheated_sodium`) |

The Task 038 loader still applies low-to-high resource stacks, `replace`, ordered writes, ordered
removals, tags, and layer-local failure isolation. Unknown-descriptor diagnostics are scoped to the
exact registry folder, so the ordinary damage map does not warn about chemical sibling descriptors
and vice versa. `FabricChemicalDataMapSession` loads all five results into one candidate, validates
all chemical keys, copies every map, captures the current server policy, then publishes one immutable
increasing epoch. A fatal owner/catalog/reference failure retains the preceding complete local epoch;
a malformed individual resource layer retains the loader's documented layer-local behavior.
Disappeared map files produce empty maps on the next successful epoch.

`ChemicalDataContext` is immutable and selected with a `ThreadLocal`; server and physical-client
owners therefore derive attributes for the same canonical `Chemical` objects without mutating shared
chemical instances. Modern data-map values replace corresponding legacy attributes when present;
removal falls back to the admitted legacy state. Radiation validation and tooltip/tank queries read
the current owner context. Server stop and either configuration- or play-phase client disconnect clear
the binding. An integrated server thread and render thread can retain distinct epochs in one JVM.

`Chemical.updateFromDataMap` remains public, virtual, `@Internal`, and participates in every top-level
context-dependent derivation query. Overrides must invoke `super`; a reentrant query on the same
Chemical sees the immutable partial derivation accumulated so far, while a nested query of another
Chemical gets an independent frame and restores the outer frame afterward. Direct external invocation
is intentionally invalid: the base method throws outside the context-local derivation callback. This
is an explicit authorized Fabric delta from upstream's shared cache-refresh lifecycle. The per-query allocation and
five descriptor lookups were replaced by Task 041 with the exact-context/thread-local hot cache described in `FABRIC_RECIPES.md`; no global mutable Chemical state
was introduced. `FabricChemicalDataMapSession.publishSnapshot` is package-private and exists only as
an isolated same-package test seam, not as a runtime publication API.

`MekanismServices` has one deterministic policy for both selected public services: exactly one
provider must exist, while zero or multiple providers fail explicitly. The packaged descriptors are
byte-exact:

```text
META-INF/services/mekanism.api.text.ITooltipHelper
  mekanism.common.util.text.TooltipHelper
META-INF/services/mekanism.api.datamaps.IMekanismDataMapTypes
  mekanism.fabric.datamaps.FabricChemicalDataMaps\n
```

The complete data-map provider delegates Task 038 damage-map queries to its existing server owner and
serves all five Chemical maps from the active context. Direct Chemical holders must canonicalize to a
registered value; reference holders must belong to `ChemicalRegistry.REGISTRY_KEY`.

## Configuration ownership

Startup still has its independent 12-value owner. Task 040 additionally loads exactly one local
`config/Mekanism/common.toml` owner with the official five values: `energyType=FORGE_ENERGY`,
`temperatureUnit=KELVIN`, and enabled decay timers, block-data copying, and holidays. Enum parsing is
strict by declared serialized name/value and correction occurs before publication.

For each real server identity, `FabricChemicalConfig` claims the normalized
`<world>/serverconfig/Mekanism/general.toml` path and owns only:

```text
radiation.enabled = true
energy_conversion.feConversionRate = 2.5       # finite, 0.0001..10000
energy_conversion.blacklistForge = false       # world-restart metadata
```

Loading and correction preserve all unowned TOML. Claims are exclusive; startup failure releases the
claim, and server stop unloads the fragment and restores official default policy. Local common units
are never overwritten by a connection snapshot. Only the three behavior-relevant server policy
values travel with the Chemical epoch and are cleared on disconnect. `ChemicalConfigAccess` is the
no-Fabric common seam; `RadiationValidation` intentionally exposes only enabled policy plus the
official `1e-7` baseline and is not an `IRadiationManager` substitute.

## Bounded synchronization

The original exact `10.7.19` configuration negotiation remains the first compatibility gate. Task 040
adds:

| Phase/direction | ID | Purpose |
|---|---|---|
| configuration S2C | `mekanism:chemical_snapshot` | initial five-map/policy epoch |
| configuration C2S | `mekanism:chemical_snapshot_ack` | exact session/epoch publication ACK |
| play S2C | `mekanism:chemical_snapshot` | successful reload epoch |

Each connection receives a fresh opaque session ID. The server's ordered Chemical
`ConfigurationTask` requires receiver support and snapshots the server owner on the server executor.
ACK processing re-reads that authoritative owner on the same executor. If its epoch advanced after
the sent snapshot, the task updates its pending epoch, sends a same-session strictly newer snapshot,
and remains open until a second exact ACK; stale, regressing, wrong-session, or otherwise invalid ACKs
disconnect. Completion therefore cannot publish a stale join epoch. Pending state is per configuration
handler; play session state is per player UUID. Configuration disconnect removes both maps, including
the post-ACK/pre-play interval, while play disconnect and server stop retain their existing cleanup.

The snapshot wire envelope is bounded to a 64-character session, 128-character map-key resource
names, 65 entries per map, 325 entries total, 256 bytes per value chunk, and 65,536 value bytes total.
Every value is encoded once through its `MekanismDataMapType.networkCodec()` into a length-delimited
opaque chunk. This keeps one wire-format owner per descriptor: solid tags retain their compact
resource name, radioactivity remains its declared double codec, and coolant descriptor codecs use
resource names rather than raw holder IDs. The network thread decodes only the bounded envelope and
opaque chunks. On the owning client executor, `resolve()` verifies map-key names, invokes the same
descriptor codecs with registry-aware buffers, rejects trailing bytes/unknown references/duplicates,
and atomically publishes the context.

Malformed current-session configuration/play snapshots fail closed and disconnect. Configuration and
play disconnect callbacks hand their cleanup to the client executor. Cleanup is idempotent, clears the
session, epoch and thread-local context, and advances generation once for the live publication; stop is
likewise repeat-safe. A queued snapshot captures its generation, so dead-connection work cannot publish
or disconnect a replacement. Initial configuration accepts a same-session strictly newer catch-up
snapshot without resetting epoch, but rejects duplicate/regressing epochs and a different active
session. Epochs must be positive and strictly increase within one session. A successful server reload
broadcasts the new epoch; failed local reload/publication retains the prior context. There is still no
Mekanism-specific timeout for a peer that advertises a receiver and withholds an ACK.

## Exact packaged resources

The generated source root is not admitted broadly. Static packaging admits each of the following once
and verifies SHA-256:

| Resource | SHA-256 |
|---|---|
| `data/mekanism/data_maps/mekanism/chemical/chemical_attribute_cooled_coolant.json` | `14a5f5aec6647b753aadb2afb340fe6cc0bdbbf8f70a19665386038422cfb6b7` |
| `data/mekanism/data_maps/mekanism/chemical/chemical_attribute_fuel.json` | `4ddf704615c656b520aae009910df3430b372584f60073c229e6ba3144ca573e` |
| `data/mekanism/data_maps/mekanism/chemical/chemical_attribute_heated_coolant.json` | `550e66bcd2ee76d12554e54f40833966d90a7850ee2e4499f763c93963725391` |
| `data/mekanism/data_maps/mekanism/chemical/chemical_attribute_radioactivity.json` | `c3e432ad8af5b89256d527be0c718ecd9cad7b3ea734a6e620d43000f4f05b4a` |
| `data/mekanism/data_maps/mekanism/chemical/chemical_solid_tag.json` | `8899452dcc86851401e43dddc75b805b18466cd23462a986bd132a12475b1dca` |
| `data/mekanism/tags/mekanism/chemical/bio.json` | `cdd00ab957e9a853f213d618e891ca7d6a98f75967a93a5b23dcf201531e1880` |
| `data/mekanism/tags/mekanism/chemical/carbon.json` | `82d649000e6aecec5b154c75ae19f6b484a86326e72bfd3e437a8bbd44b8642b` |
| `data/mekanism/tags/mekanism/chemical/clean.json` | `886efcb61350c3997c64e5a301cb7bb083158f8ccb0449ea0722a520f1c89763` |
| `data/mekanism/tags/mekanism/chemical/diamond.json` | `9fecc5a3aa0de60d6e2ba6daf2b0558f56b0f6237748f056143620ea3e8c8812` |
| `data/mekanism/tags/mekanism/chemical/dirty.json` | `cccead98fd5747b5c9174263f2d04960323872021b2dc40eacd43769e775aa47` |
| `data/mekanism/tags/mekanism/chemical/fungi.json` | `cb07aff1a17f37cfe8b95b59577ed3503be6ad51cec8b267f80780eaf7cee943` |
| `data/mekanism/tags/mekanism/chemical/gaseous.json` | `e2a66162f320d3015f378ab20f6f87fbf4c3265abf3b781be15632ad81274f6f` |
| `data/mekanism/tags/mekanism/chemical/gold.json` | `92ce68c2d11a2918d6600f193de250e46e5d1fbf11eaa0d2062dd703deb2ad20` |
| `data/mekanism/tags/mekanism/chemical/redstone.json` | `092242cbc81247ff4c21f6f1402470578413568a9b9e3722609fa0f47d0c7fa0` |
| `data/mekanism/tags/mekanism/chemical/refined_obsidian.json` | `71feeecaa60653021681f435f432fa3f63311adaa34829037b86d5c08fb23798` |
| `data/mekanism/tags/mekanism/chemical/tin.json` | `bcb708d7ed1d247de6d55c17b72f879f5a8868b740d231f82d6ea78e87478646` |
| `data/mekanism/tags/mekanism/chemical/waste_barrel_decay_blacklist.json` | `21c3c44a56d76779e7a8c779f69e50c9494f2da7a3f43d22b8ebe336b39863fe` |
| `data/mekanism/tags/mekanism/chemical/water_vapor.json` | `7177474643986262ece934f42d35a24ca4bd9ed3bf4f6307e82557ebf4c7147b` |

## Source identity and delta report

Oracle: `4646f509`. All **18 `U` sources** named in `archive/TASK040_PLAN.md` were compared with
`git hash-object` against the oracle blob and are byte-identical: nine API leaves
(`BasicChemicalTank`, `ChemicalUtils`, `IMekanismChemicalHandler`, three base attribute contracts,
`IChemicalAttribute`, `IBaseProvider`, `IDisableableEnum`) and nine common leaves
(`DynamicChemicalHandler`, two chemical-holder contracts, `ProxyChemicalHandler`, `ChemicalSlotInfo`,
`ChemicalConstants`, `IChemicalConstant`, `DeferredChemical`, `ResourceType`).

The **27 `A` sources** remain the exact plan allowlist. Their deltas are limited to these audited
families: `Chemical`/stack/handlers use `ChemicalRegistry`, `ChemicalDataContext`, project NBT and no
NeoForge capability surface; data-map records use literal IDs/layer-neutral descriptors; services use
`MekanismServices`; tags/resources use literal namespace and `c:ores`; predicates use the project
`FluidStack`/`TriPredicate`; deferred registration uses existing Fabric wrappers and a relocated
private color helper; common config/units/tooltips use the selected Fabric spec and
`ChemicalConfigAccess`. No `net.neoforged.*` reference is present in any final slice.

There are **17 new (`N`) sources** (six API owners, `ChemicalConfigAccess`, and ten Fabric owners),
**six modified (`M`) platform sources** (both bootstraps, `MekanismConfigSpec`, data-map loader,
Task 038 data-map owner, and `FabricDefaultedRegistry`), and one removed/relocated (`R`) platform
descriptor. The plan expected a config-manager change, but the existing manager already supplied the
required preserving/path-claim behavior and was left unchanged. Conversely, real packaged startup
discovered that registry snapshots re-freeze a root from a reload worker, requiring the documented
idempotent-frozen repair in `FabricDefaultedRegistry`.

The exact unique touched-production-path arithmetic is therefore **69**, not the plan headline's 68:
33 API + 19 common + 10 new platform + 6 modified platform + 1 removed platform. The plan's numbered
platform list omitted its separately described `FabricDataMapLoader` path from that arithmetic. Net
compiled growth remains exactly **61**: 52 newly admitted API/common sources plus ten new platform
sources minus the removed platform descriptor.

## Verification

Verification surfaces are:

- `./gradlew -p fabric --no-daemon --console=plain test fabricTransferTest fabricRegistryTest fabricDataMapTest verifyFabricTestContract remapJar`;
- `./gradlew -p fabric --no-daemon --console=plain check build`;
- `./fabric-build.sh` (ordinary release-contract build, not parent-only clean release);
- `FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh` and full packaged dedicated-server mode;
- `scripts/fabric-chemical-runtime-smoke.sh`;
- `scripts/fabric-datamap-reload-smoke.sh`;
- `scripts/fabric-paired-network-smoke.sh`;
- `scripts/fabric-configuration-rejection-smoke.sh`;
- `scripts/fabric-client-window-smoke.sh`.

The Chemical runtime gate uses one real dedicated server and one physical Xvfb client process. Its
first real configuration publishes and ACKs epoch 1, then a later ordered fixture task deliberately
terminates the connection before play. Real Fabric disconnect callbacks prove client epoch zero,
generation advance, stable no-revival ticks, and server removal of the pre-play UUID/session; a
successful new configuration with a distinct session proves the old session was cleared. The same
client then retains the original play disconnect/reconnect, exact 65-entry declaration order/name
remap, initial nuclear-waste radioactivity/tank rejection/tooltip, unknown-name rejection, exact
`(1L << 60) + 123` NBT/stream round trips, datapack removal at epoch 2, tank acceptance/tooltip
removal, and same-JVM owner isolation. No callback is faked with a null handler. Fixture classes and
metadata remain isolated from the release JAR by Gradle and static artifact gates. The fixture observes
pre-play server insertion through a package-private read-only seam; only production callbacks mutate
or remove networking state.

Mandatory independent GLM 5.3 high review `bf00719f-3d49-48c` independently reran the focused and Knot
suites and returned **ACCEPT WITH FIXES** after 845.8s, 195.3k tokens, 91 tool calls, about `$2.56`.
It identified missing client configuration-disconnect cleanup, leaked server pre-play UUID sessions,
the snapshot-send/ACK reload race, reflection-only lifecycle evidence, inaccurate prior review
attribution, an undocumented public snapshot seam, and the unresolved public/overridable
`Chemical.updateFromDataMap` boundary and per-query cost. Corrective `ff7e5f7c` closed those findings
with the lifecycle/race logic, focused contracts, real abort-before-play regression, narrowed seam,
precise virtual/reentrant Javadocs and explicit Task041 caching gate described above.

Focused GLM 5.3 high rereview `cd70ccdf-36bc-476` used 888.7s, 142.3k tokens and 82 tool calls
(about `$1.47`). It confirmed every original finding closed with no correction regression; its sole
bounded required fix was an executable nested different-Chemical frame-restoration assertion, with no
further rereview required. Parent commit `0e719682` added that assertion without changing test totals,
and merge commit `f24ce94e` integrated the vertical.

At `f24ce94e`, the parent independently passed one 6m47s combined gate: full `check build` in 53s,
`fabric-build.sh` in 2s, static packaged checks in 10s, packaged server through `Done` in 39s, real
Chemical configuration-abort/reconnect in 55s, data-map reload in 47s, paired network in 68s,
three-case rejection in 96s, and client-window regression in 37s. The Chemical gate proved pre-play
cleanup with no revival, three distinct ACK sessions, two play joins and final epoch 2. After the
integration documentation commit `6a0ceef3`, clean release passed in 44.56s with exactly one JAR,
SHA-256 `1a49b984bdb766d68337e7ff1055edb7bb21245ae8a3b6aa6de3eb126a52590f`, and build-info source
`6a0ceef37986bc2cd2076607b521333b9b295a4e`. It was rerun after the final evaluation commit so source
metadata aligns to that HEAD with the same one-JAR hash. No push, deployment, production, or mirror
operation occurred.
