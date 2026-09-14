# Task052 continuation10 — OBJ/MTL geometry owner delivered (partial, model-transition handoff)

Parent contract: `docs/TASK052_CONTINUATION_10.md`. Baseline `f7d899d7` + merged WIP `9cc0a0b8` (branch
`pi-agent-0dfe7272-607b-45b`, merge `29305d9d`, all ancestry preserved).

## Delivered this batch (committed)

Narrow Mekanism transmitter OBJ/MTL owner in `fabric/src/main/java/mekanism/fabric/client/render/obj/`:

- **`TransmitterObjGeometry`** — pure-Java parser (zero Minecraft deps) for the shipped
  `transmitter_{small,large}.obj.mek` + `transmitter_{small,large}.mtl` + `transmitter_glass_large.mtl`:
  `v/vt/vn`, `o` groups, `usemtl`, `mtllib` (multi), quads in `v`, `v/vt`, `v/vt/vn`, `v//vn`, negative indices,
  `flip_v` (applied at parse), `newmtl`, `map_Kd #alias` (non-`#` reference = hard diagnostic failure),
  `forge_TintIndex`. Unsupported directives/groups-without-faces fail loudly — no silent geometry drop.
  Immutable after parse (unmodifiable group/material maps).
- **`TransmitterQuadBaker`** — bakes one group's faces into vanilla `BakedQuad`s (1.21.1 BLOCK layout:
  pos 3f, color 4ub, uv0 2f, light 2s, pad) with `TextureAtlasSprite#getU/getV(alias*16)` mapping, material
  tint index preserved (glass MTL `forge_TintIndex 1`), per-part end-icon rotation quaternion applied about block
  center, direction from declared face normal (`Direction.getNearest`). Quad-only contract enforced.

## Verification (actual, not structural)

1. **Shipped-asset check** (`/tmp/t052-obj-check/TransmitterObjGeometryCheck.java`, plain javac/java):
   small + large internal and glass-mtl_override parses → `TRANSMITTER_OBJ_GEOMETRY_CHECK PASS`.
   Facts verified: exactly 24 groups per model = {down,up,north,south,west,east} x {NONE,NORMAL,PUSH,PULL}
   (identical to `getPartName(side, connectionType)` in NeoForge `TransmitterBakedModel`); 270 quads, 0 non-quads;
   small 360 v / 288 vt / 96 vn, large 360 v / 233 vt / 120 vn; 9 materials each; all face indices in range;
   flip_v applied; all 9 glass materials carry tintIndex 1; `mtl_override` semantic = replace the
   `mtllib`-declared library (`transmitter_large.mtl` key → glass MTL content).
2. **Subset compile** of both new classes against pinned mojmap MC 1.21.1 merged jar + JOML + jetbrains
   annotations: **SUBSET_COMPILE_PASS** (`javac -d /tmp/t052-subset`, 13:38 UTC+8).
3. `./gradlew -p fabric -I scripts/task052-owner-compile.init.gradle compileJava` still fails with the
   **pre-existing** 1,120 excluded-slice "symbol not found" errors (log `/tmp/t052c2_compile.log`); **zero**
   errors reference the new obj classes. No full build claimed.

## Not yet done in this batch (exact remaining chain, for CC DeepSeek continuation)

1. **Consumer**: fabric `FabricTransmitterBakedModel implements FabricBakedModel` — `emitBlockQuads` reading
   `RenderAttachedBlockView` attachment, six SidedConnection part caches (internal + glass), TransmitterDataKey on
   immutable snapshot, item/default fallback = all-NONE parts. Blocked on: needs `TransmitterModelData`,
   `ConnectionType`, `EnumUtils` from `src/main` — none admitted to `fabric/common-slice.txt` today; compiling the
   consumer via subset javac drags the `ConnectionType` closure (`MekanismLang`, `IIncrementalEnum`, ...), which is
   the parent's slice-admission decision per the contract's audit rule.
2. **Producer**: `TileEntityTransmitter` `RenderAttachmentBlockEntity` snapshot through existing
   `initModelData/updateModelData` hooks (both hooks verified present at lines 310/318; Diversion override at
   `TileEntityDiversionTransporter:39`); published `TransmitterModelData` must become immutable snapshot (current
   `EnumMap` is mutable and `getConnectionsMap()` exposes it — cache-key hazard the contract forbids).
3. **Registration**: `ModelLoadingPlugin` from `MekanismFabricClientBootstrap` (resolver for the three
   `loader: mekanism:transmitter` JSONs: `transmitter/small/small.json`, `transmitter/large/large.json`,
   `transmitter/large/logistical_transporter/transporter.json`) + `ColorProviderRegistry.BLOCK` tint; texture alias
   → sprite resolution incl. opaque config remap (`center_*_opaque`/`side_opaque` aliases present in shipped MTLs).
4. Lifecycle: reload invalidation of sprite-bearing caches, opaque-config key, `TileEntityUpdateable.updateModelData`
   update path re-check.

Model transition at 14:00 UTC+8 reached mid-batch; no runtime/packaged/full-suite claims. No production,
mirror, deploy, push, reference writes, or broad scans.
