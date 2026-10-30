# Task052 review17 repair — temporary Astra

## Baseline and scope

Started from clean parent carrying REVIEW17; merged preserved `77445b44` first (merge `ae14b242`), retaining `c935d377`, `570954d5`, `9cc0a0b8` ancestry. Binding boundaries: `TASK052_REVIEW_17.md`, `TASK052_CONTINUATION_10.md`. This is a renderer/config prerequisite repair, not Task052 acceptance or a usable/visually verified machine. No slice expansion, full QIO owner, runtime fixture, production/deployment, push, index, reference write/copy, or new implementer.

## Real owner changes

- `TileEntityLogisticalTransporter.updateModelData` captures the actual `EnumColor.getPackedColor()` (null -> -1) alongside existing hasColor into canonical `TransmitterModelData`. Freeze prevents later color mutation. `FabricTransmitterColorProvider.COLOR`, the actual registered callback, reads only the immutable `RenderAttachedBlockView` attachment for tint1; absent/uncolored/Diversion/item context -> -1. No live tile lookup or constant white. Existing color-identity refresh route in `LogisticalTransporter.handleUpdateTag` and transmitter update/freeze publication remains unchanged.
- Existing sole manager-loaded `FabricWindowConfig` now also owns strict boolean `accessibility.opaqueTransmitters=false`, with official `gameRestart` metadata. `FabricClientConfigLifecycle` loads it and publishes a process-pinned actual value before model plugin initialization, removes it during physical shutdown, and unwinds partial installation. Unbound getter now fails; it does not invent a false setting. Window async saver/drain and unknown TOML keys remain intact. `FABRIC_CONFIG.md` describes this extension, not a second same-file owner or full ClientConfig activation.
- `getDependencies()` returns only vanilla model dependencies (parents/override model ids), never prepared JSON/OBJ/MTL resource ids. Resource reads stay in the preparable asset owner. Discovery compares the exact JSON loader field; malformed loader values and explicitly wrong loader asset reads fail.
- Block model constructor preserves vanilla ambient-occlusion, GUI light, and override declarations, including inheritance of absent fields. Bake passes the actual ModelState, transforms, light/AO and overrides into the baked owner.
- Quad baking composes incoming model rotation with per-end-icon rotation in official order, converts center-relative transformation to corner-relative positions, transforms each declared normal by the normal matrix, and packs signed normal bytes rather than zero padding. Triangles duplicate their final corner, matching the OBJ representation. Shipped MTL `Kd`/`d` diffuse ABGR and `Ka` ambient light now reach the quad; shipped zero Ka means shade=true, rather than the previous shade=false. All shipped material sets use Kd=0.8/Ka=0/d=1.
- Direct source comparison exposed two related construction discrepancies: texture remapping must detect the directional **texture alias suffix**, not the connection-part prefix; the default item arrangement uses unremapped VisibleModelConfiguration textures. Both now match original owner behavior. Particle alias failures are no longer swallowed into an invented side-texture fallback.

## UV-lock decision and reference evidence

Read the actual original Mekanism `TransmitterLoader`, `TransmitterModel`, `TransmitterBakedModel`, `TransmitterModelConfiguration` and shipped assets. Read (without copying into this repository) pinned NeoForge **21.1.200** source JAR entries `net/neoforged/neoforge/client/model/obj/ObjLoader.java` and `ObjModel.java` from the exact resolved module directory.

Important correction to the prior assumption: `ObjModel.ModelMesh.addQuads` consumes **only `modelTransform.getRotation()`** (plus owner root transform); `makeQuad` maps authored OBJ UVs directly. It never applies `ModelState.isUvLocked()`. Mekanism passes that flag through SimpleModelState, but the real OBJ owner does not use it, unlike vanilla cuboid FaceBakery. This repair preserves that behavior: the complete ModelState reaches the narrow baker, positions/normals use its transformation, and authored UVs remain unchanged for either UV-lock flag. It does **not** invent a different vanilla cuboid UV projection for OBJ geometry. This semantic finding was reported at the progress checkpoint. No root transform is declared in the shipped transmitter models.

The parser/baker remains shipped-transmitter-specific, not a general OBJ/options framework. Config reload/live toggle, arbitrary OBJ extensions, automatic cull optimization, and client visual verification are not claimed.

## Executed verification

Classpath generated fresh through Loom 1.13.6 for Minecraft 1.21.1 official mappings and resolved/remapped Fabric APIs:

```sh
./gradlew -p fabric -I "$PWD/scripts/task052-renderer-classpath.init.gradle" printMainCompileClasspath --no-configuration-cache -q
CP=$(<fabric/build/task052-main-compile-classpath.txt)
javac -proc:none -nowarn -cp "$CP" -d fabric/build/review17-final \
  -sourcepath src/api/java:src/main/java:fabric/src/main/java \
  fabric/src/main/java/mekanism/fabric/config/FabricClientConfigLifecycle.java \
  fabric/src/main/java/mekanism/fabric/client/render/obj/*.java
scripts/task052-transmitter-check.sh
```

- **PASS actual renderer/config compilation**, including canonical model data dependencies: `TASK052_REVIEW17_FOCUSED_COMPILE.log`.
- **PASS existing focused asset/model chain checks**: `TASK052_REVIEW17_MODEL_TEST.log`. Small/large each 24 groups/270 quads; 47 models/3 bases discovered. Added assertions inside the existing check for the actual registered tint callback with RED/DARK_BLUE snapshots (a view proxy rejects any non-attachment/world access), freeze rejection, null/uncolored/Diversion handling, MTL diffuse/ambient values, exact loader matching, model-vs-resource dependency ids, vanilla flags, actual GUI/ground/first-person transform values. Missing vanilla parents now fail; `block/block` is read directly from the pinned Loom Minecraft JAR, not an empty surrogate.
- **PASS all six existing `FabricWindowConfigTest` methods**, using the actual compiled config owners and JUnit Platform launcher on the Loom-resolved test runtime classpath: `TASK052_REVIEW17_CONFIG_TEST.log`. Existing methods now also check 28 window scalars plus the gameRestart setting, actual TOML true publication, shutdown removal and unrelated accessibility-key retention. Existing injected saver failures are expected and tests pass; no extra test methods or new fixture.
- Script/header now explicitly says sprite-free parsing/model-chain checks **do not execute quad baking**. Rotation/normal/shading implementation is compiled and source-compared, not visually or sprite-runtime verified. No old full item-transaction test rerun.

## Honest remaining build blockers

`./gradlew -p fabric compileJava --no-configuration-cache` **fails**: actual log `TASK052_REVIEW17_SLICE_COMPILE.log.gz`. First errors include `QIOItemViewerContainer` missing `VirtualCraftingOutputSlot`/`InventoryUtils`, and `MekanismUtils` missing `MekanismAPI`, `FrequencyAware`, `IFrequencyItem`, `OwnerDisplay`; broader root/container owners remain unresolved. No claim of baseline equality from error totals.

An explicit javac attempt on the actual changed `TileEntityLogisticalTransporter` with the same remapped classpath/full real source paths also **fails** on its unported dependency closure: `DynamicNetwork` still imports NeoForge EffectiveSide; `CompatibleTransmitterValidator`/computer wrappers use NeoForge FluidStack; base tile/registration owners require NeoForge capability/event/registry classes. Exact bounded diagnostics in `TASK052_REVIEW17_TILE_COMPILE.log.gz`. Thus the actual renderer/config owners compile, but the real tile closure does not yet compile and the producer hook is source-reviewed rather than falsely declared fully compiled. No stubs, fake tile, or slice widening used to hide this.

No server/client runtime, release artifact, block registration availability or visual acceptance was performed. Parent should review the actual repair diff; next implementer returns to the user's standing normal time-based model policy, not persistent Astra.
