# Task052 continuation10 — REVIEW15 defect repairs (WIP handoff, 13:58 UTC+8)

Baseline: merge `902e98ca` (parent `eee593a0` + preserved WIP `570954d5` incl. `9cc0a0b8`).
Commit of this batch: **`9f2e41d4`** (all REVIEW15-listed repairs landed) plus this evidence doc.

## Repairs delivered in `9f2e41d4` (per REVIEW_15 checklist)

1. **Consumer really emits quads with materials** — `FabricTransmitterBakedModel.emitQuads` now uses
   `emitter.fromVanilla(quad, material, null)` **followed by `.emit()`**; CUTOUT material for internal parts,
   TRANSLUCENT for the glass pass, created via `RendererAccess.INSTANCE.getRenderer().materialFinder()`
   (verified by javap: `Renderer` has no static `get()`, `RendererAccess.INSTANCE.getRenderer()` is the pinned
   fabric-renderer-api-v1 3.2.2 access). Tint index stays in the vertex data (`fromVanilla(BakedQuad,...)`.
2. **Consumer is a real vanilla BakedModel** — now `implements BakedModel, FabricBakedModel`:
   `getQuads` (unculled → default item arrangement), `useAmbientOcclusion/isGui3d/usesBlockLight=true`,
   `isCustomRenderer=false`, `getParticleIcon` (`particle` alias, `side` fallback), `getTransforms` =
   `ItemTransforms.NO_TRANSFORMS`, `getOverrides` = `ItemOverrides.EMPTY`. Loader-result contract satisfied without
   an unused helper.
3. **UV *16 removed** — `TransmitterQuadBaker` now calls `sprite.getU(u)/getV(v)` with normalized 0..1 input
   (pinned 1.21.1 semantics). **Normal rotates with geometry**: `rotation.transform(normal)` before direction
   derivation/packing.
4. **Alias normalization consistent** — parser strips `#` (unchanged); consumer `adjustAlias` now produces and
   compares stripped names (`side`, `side_glass`, `side*_opaque`, `center_glass_opaque`, `center_opaque`), so the
   opaque/icon remap actually executes. Same normalized form flows to the sprite lookup.
5. **Deep immutability** — `TransmitterObjGeometry` constructor wraps every group's face list in
   `Collections.unmodifiableList(new ArrayList<>(...))` before the unmodifiable map; records carry no mutable state.
6. **Snapshot never exposes mutable map** — `TransmitterModelData.connectionView` is a final field initialized to
   `Collections.unmodifiableMap(connections)` (protected pre-freeze too); `freeze()` only sets the flag.
7. **EnumUtils trim (parent-adjudicated)** — `TransmitterModelData.check()` uses cached
   `Direction[] DIRECTIONS = Direction.values()` (same declaration order); EnumUtils import removed. No QIODriveArray
   drag.
8. **Producer NeoForge path replaced** — `TileEntityTransmitter.TRANSMITTER_PROPERTY` and `getModelData()` removed
   with the `ModelData/ModelProperty` imports; the sole Fabric path is `getBlockEntityRenderAttachment()` → frozen
   snapshot via `initModelData()/updateModelData(data)` (Diversion subtype preserved). Reference comment left at the
   old usage site in the official NeoForge `client/render/obj/TransmitterBakedModel.java` (not compiled in the fabric
   slice) so the reference file stays syntactically intact.
9. **Artifact/check cleanup** — `javac.20260914_134426.args` deleted (git rm); asset check renamed to
   `scripts/TransmitterObjGeometryCheck.java` (filename == public class, header comment gives the exact
   `javac`+`java` invocation against the shipped assets dir — no external rename needed).

## Honest compile status (NOT success claims)

- Plain-javac subset compile of the fabric obj package against pinned mojmap MC 1.21.1 merged jar
  (`~/.gradle/caches/fabric-loom/minecraftMaven/.../minecraft-merged-1.21.1-loom.mappings...jar`) +
  module-cache fabric api jars progresses past all earlier syntax/API errors after the `RendererAccess` fix,
  but the module-cache fabric api jars are **intermediary-mapped** (`net.minecraft.class_1920/2338/777`
  unresolvable), and the full api-slice closure drags more netty/brigadier jars. A mapping-remapped fabric api
  classpath is needed; it was not available in the caches probed. **No compile pass is claimed.**
- `./gradlew -p fabric -I scripts/task052-owner-compile.init.gradle compileJava` diagnostic run was started to get
  real gradle diagnostics but was **terminated at the 14:00 safe-handoff boundary before completing** — no result,
  positive or negative. First attempt failed trivially (init-script path resolved relative to `fabric/`); use
  `-I "$PWD/scripts/task052-owner-compile.init.gradle"` from repo root. Log stub: `/tmp/t052c3_compile.log`.
- Previous batch's facts still stand: OBJ/MTL parser + baker subset-compiled and the shipped-asset check passed
  (24 groups/270 quads per model); consumer+producer still lack a completed compile pass.

## Exact remaining work for the next (CC DeepSeek) implementer

1. **Compile pass**: rerun the gradle diagnostic compile (correct `-I "$PWD/..."` invocation). Watch for: consumer's
   `ConnectionType`/`TransmitterModelData` closure into the fabric source set (EnumUtils blocker now removed at the
   TransmitterModelData level, but `ConnectionType` itself still drags netty/StreamCodec — those jars exist in the
   gradle-resolved classpath, unlike plain javac). Fix any residual errors in the obj package only.
2. **Rerun the reproducible asset check** after the alias-normalization change:
   `javac -d /tmp/t052c scripts/TransmitterObjGeometryCheck.java fabric/.../TransmitterObjGeometry.java && java -cp /tmp/t052c TransmitterObjGeometryCheck <assets dir>` — parser semantics unchanged, expected PASS.
3. **ModelLoadingPlugin registration** (not started): real Fabric `ModelLoadingPlugin` from
   `MekanismFabricClientBootstrap` for the three `loader: mekanism:transmitter` JSONs
   (`transmitter/small/small.json`, `transmitter/large/large.json`,
   `transmitter/large/logistical_transporter/transporter.json`): resolve obj.mek + mtl_override → parse →
   `FabricTransmitterBakedModel` (now a real `BakedModel`, so it can be the loader result directly), child-tier
   inherited texture aliases incl. `center_glass_*`/`*_opaque`, `flip_v` handling, opaque config in cache key.
4. **ColorProviderRegistry.BLOCK** tint (index 1 logistical color) + lifecycle: reload invalidation of sprite caches,
   `updateModelData` rerender path re-check, color-to-color update rerender.

No runtime/visual verification performed or claimed; production/mirror/deploy/push/index/reference untouched.
