# Task052 continuation11 — concrete Fabric transmitter renderer closure (CC DeepSeek)

Baseline: parent `a2713068`, with `c935d377` merged first (merge commit `1bdadf4c`), preserving `570954d5` and
`9cc0a0b8`. Contracts: `docs/TASK052_CONTINUATION_10.md`, `docs/TASK052_REVIEW_16.md`.

## REVIEW16 defects: all five fixed

1. **Face deep immutability.** `TransmitterObjGeometry.Face` is no longer a plain record over the parser's mutable
   `ArrayList`; its compact constructor now does `vertices = List.copyOf(vertices)`. Check 2 in the focused script proves
   both the face corner list and the enclosing group list reject mutation.
2. **`getTransforms` no longer discards JSON transforms.** `FabricTransmitterBakedModel` takes and returns real
   `ItemTransforms`; the model parses `display` through vanilla's own `BlockModel.fromString(json).getTransforms()`
   (not a hand-rolled identity) and the parent walk completes inheritance. Check 3 asserts the result is not
   `NO_TRANSFORMS` and that the shipped `block/block` display values are inherited.
3. **Excluded `TransmitterBakedModel` body restored.** Reverted to `extraData.get(TileEntityTransmitter.TRANSMITTER_PROPERTY)`;
   the `data = null` fake no-op is gone. That file stays outside both slices (it needs NeoForge `ModelData`), so its
   missing producer field is an honest migration diagnostic rather than something silenced.
4. **Producer provider migrated.** `CONFIGURABLE_PROVIDER` is now the Fabric `BiFunction` accepted by
   `BlockApiLookup#registerForBlockEntity`, using the already-established `CapabilityTileEntity#capabilityProvider`
   contract with the same lazy resolver and fail-closed guards.
5. **Reproducible check command.** `scripts/task052-transmitter-check.sh` supplies the pinned classpath (annotations jar
   included), so the previous `/tmp/t052c` vs `/tmp/t052` header mismatch is gone.

Also corrected in the same batch: the producer attachment override was named `getBlockEntityRenderAttachment`, but the
real `RenderAttachmentBlockEntity` method is `getRenderAttachmentData()` (the former is the *view's* method). The old
override silently implemented nothing.

## Real model-loading closure delivered

- `FabricTransmitterModelPlugin` — a real `PreparableModelLoadingPlugin` + `ModelLoadingPlugin.Context#resolveModel()`
  registration, wired from `MekanismFabricClientBootstrap`. Assets load off-thread; each resource reload creates a fresh
  context and asset map, so every sprite-bearing baked model is rebuilt rather than served from a stale cache.
- `FabricTransmitterModels` — bounded discovery (only `models/block/transmitter/**` and `models/item/**`) of the
  transmitter loader bases and every descendant whose `parent` chain reaches one. No hard-coded id list.
- `FabricTransmitterAssets` — real `ResourceManager` reads of the loader JSON, OBJ and MTL, with `flip_v`,
  `glass.flip_v` and `glass.mtl_override` honoured (the override replaces the `mtllib`-declared library's content while
  the OBJ text is untouched, matching NeoForge's OBJ loader).
- `FabricTransmitterBlockModel` — the real `UnbakedModel`/`BlockModel` subclass. It must subclass `BlockModel` because
  vanilla parent resolution hard-requires parents to be `BlockModel`s (the shipped item models parent to tier models)
  and vanilla's texture walk reads the `textureMap` *field* directly, so the alias map is rebuilt from this model's own
  JSON using vanilla's exact `#`-reference/Material rule.
- `FabricTransmitterColorProvider` — real `ColorProviderRegistry.BLOCK` registration for the four logistical transporter
  blocks found in the live block registry, with the shipped `forge_TintIndex 1` contract.
- `TransmitterModelConfig` — access seam for `opaqueTransmitters`.

## Verification (actual)

- `scripts/task052-transmitter-check.sh` → **PASS**: real OBJ/MTL parse of the shipped small/large assets (24 parts each,
  270 quads, `flip_v` applied, glass override tinted), Face/group immutability, and the real model chain over the shipped
  pack (`discovered=47 bases=3 tier_aliases=ok transforms=inherited`).
- Renderer package compiles clean against the **Loom-resolved remapped** classpath
  (`fabric/build/task052-main-compile-classpath.txt`, produced by
  `scripts/task052-renderer-classpath.init.gradle printMainCompileClasspath`).
- `verifyPlatformSliceNoNeoForge`, `verifyCommonSliceNoNeoForge`, `verifyApiSliceNoNeoForge` → **BUILD SUCCESSFUL**.
- `./gradlew -p fabric compileJava` still reports the **pre-existing** 387 excluded-slice errors (unchanged count
  before/after; log `/tmp/t052-final.log`). **Zero** of them name any file added or changed in this batch.

## Honest limits

- `ClientConfig` cannot compile in the current Fragile client slices: its QIO container closure needs
  `VirtualCraftingOutputSlot`/`InventoryUtils`, and it calls NeoForge's patched `ResourceLocation#compareNamespaced`.
  `opaqueTransmitters` therefore flows through `TransmitterModelConfig`; with no owner installed it returns the official
  default `false` **and logs a warning** rather than silently pretending the option works.
- Sprite production needs LWJGL natives and a live client, so check 3 stops at the sprite boundary; quad emission is
  covered by the compiled owner plus check 1's real-asset geometry, not claimed as runtime-verified.
- No usable/visually-verified renderer claim: block registration and client runtime are outside this request.

No production/mirror/deploy/push/index/reference writes.
