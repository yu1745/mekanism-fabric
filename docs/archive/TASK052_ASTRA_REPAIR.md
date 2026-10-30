# Task052 Astra repair — committed source WIP, NOT accepted

Ancestry: clean parent `b8f49530` → merge `574f053e` of preserved `47ee0e0c` (including all preceding WIP) → source repair `5c52ce5b`.
Task051 remains last accepted. No production/mirror/deploy/push, reference writes, index, model fallback, or additional implementer.

## Actual repair (not a claim of production compilation)

- **Request:** retain original HandlerItemData identities *and explicit original totalCount* on abort; reject invalid extraction amounts/debit bounds; keep positive partial discovery and exact-debit failure from preserved WIP. Ignore empty selected candidates.
- **Route:** checked/RR source gating now includes current transmitter world identity. `doEmit=false` uses abort-only nested transactions; legacy wrappers do not commit simulation. Non-transactional PathCalculator paired with a transaction is rejected. The TransactionContext checked overload is public (the real TransporterItemStorage caller previously could not access it).
- **Explicit prediction chain:** Stack → Pathfinder → InventoryNetwork → TransporterManager, plus pathfinder's TransporterUtils target validation, forwards the caller context. No ambient-current transaction. Cached path validation checks current emit/receive/mutual permissions, not only color.
- **Reservation:** stack snapshot captures *actual* flowing membership, not an inference from having a target path. Preserved final-flight target/remainder/flowing transaction and deferred iterator deletion; added current final-hop connection/color validation.
- **Endpoint/cache:** invalidation permanently revokes old TransporterItemStorage aliases; resolver and all three direct endpoint-routing consumers check revocation. ItemAcceptorCache checks actual world tile identity; network snapshots require current emit permission.
- **Immediate callers:** exporter uses checked insertMaybeRR rather than its invalid seven-argument recalculatePath call; quotas use accepted amount captured before exact QIO debit. Ejector rebuilds selected physical-slot requests per real sided provider instead of rebinding a handler while retaining old-side StorageViews. Miner captures the accepted replacement before debit. Bin validates/debits through real DOWN exposure for every tier (creative extraction still never depletes at the actual slot/adapter owner). Sorter home prediction does not recurse through another sorter.
- **QIO:** frequency participant exclusively owns shared totalCount, prior isDirty/needsUpdate/updatedItems and wildcard caches, alongside ordered item-map identities and indexes. Type participant owns count/containingDrives only; no LIFO shared-total assumption. All type-removal paths join frequency first. Drive data binds its original physical stack and checks identity before mutation; metadata rollback and surviving final-commit holder save remain. The actual-owner probe found and repair fixed nonexistent BiMultimap.keySet/get calls and direct deferred-holder arguments to vanilla component APIs.
- **Packet:** TransporterStack's seven-field codec now directly uses vanilla StreamCodec with identical field order, optional encodings and progress-zero normalization, instead of an unported NeoForgeStreamCodecs import.
- Read both BinInventorySlot and ComponentBackedBinInventorySlot: actual creative external insertion/extraction combine EXECUTE with false; empty creative bins do not acquire a type. FabricItemStorage's finite branch still requires exact full readback. This was source inspection, not an executed creative test.

## Verification and reproducible evidence

1. Initial `./fabric-build.sh`: fails compileJava, first 100 missing-symbol diagnostics; no tests executed.
2. `./gradlew -p fabric compileJava --console=plain`: fails, not a successful production compile. Final committed-source repeat also requests the three slice checks. Compressed full log: `TASK052_ASTRA_SLICE_COMPILE.log.gz`.
3. Three focused existing structural gates pass: `verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge verifyPlatformSliceNoNeoForge`; full output `TASK052_ASTRA_FOCUSED.log`. They cover the existing slices only, NOT excluded Task052 owners.
4. Actual-owner diagnostic probe:
   ```sh
   ./gradlew -p fabric -I "$PWD/scripts/task052-owner-compile.init.gradle" compileJava --console=plain --no-configuration-cache
   ```
   This temporarily includes the explicitly listed real source owners in javac and raises diagnostic truncation. It does not create fake owners, change the production slices, or establish successful compilation. Full final output: `TASK052_ASTRA_OWNER_COMPILE.log.gz` (1,120 diagnostics, many cascading from missing base types). Corrected QIO metadata/snapshot API errors and TransporterStack codec error disappeared; that is diagnostic progress, NOT proof those production classes compile. The final probe did not include TileEntityLogisticalTransporterBase (its direct resolver change still needs production compilation).
5. `git diff --check` passes. No full test suite, runtime fixture, clean release, or isolated participant-pattern test was used as acceptance. No baseline diagnostic equivalence is claimed.

## Concrete unfinished compilation/ownership boundary

This repair is not the completed connected owner admission. Production common slice still excludes TransitRequest, HandlerTransitRequest, InventoryNetwork, transporter/pathfinder and immediate tile callers. Source-only fixes cannot satisfy the build requirement. Do not declare R1–R7 or Task052 accepted.

Immediate dependencies requiring a real port, not more count-based admission or fake/no-op holders:

- `LogisticalTransporterBase extends Transmitter<...>` → `TileEntityTransmitter`. Its public `getModelData()` and `TRANSMITTER_PROPERTY` still use NeoForge ModelData/ModelProperty; the actual consumer is `client/render/obj/TransmitterBakedModel.getQuads(..., ModelData, ...)` (line 103 reads that property). Porting the full tile while preserving behavior therefore needs the real Fabric transmitter model/render-data lifecycle, shared by all transmitter types. `CONFIGURABLE_PROVIDER` also still uses NeoForge ICapabilityProvider even though CapabilityTileEntity already provides the Fabric BiFunction contract. I did not remove rendering or install a no-op model-data substitute to claim a compile.
- `QIOFrequency` imports `CommonWorldTickHandler` and reads `flushTagAndRecipeCaches` in tick. The real reload/tick invalidation owner must be connected; silently dropping that branch or inventing a static boolean would truncate behavior.
- `MekanismUtils` imports excluded official `MekanismAPI`; its original class constructs NeoForge registries, while FabricChemicalRegistryLifecycle already owns the canonical chemical registry. A root-API admission must preserve that existing canonical identity instead of constructing a second chemical registry. Other first diagnostics include the real QIO viewer's VirtualCraftingOutputSlot and Mekanism container type owners; these remain unresolved.

**Next decision:** extend implementation explicitly through the actual transmitter Fabric rendering/model-data owner and root registry/reload owners above, or retain Task052 excluded until their coherent port is ready. Merely admitting the listed source files fails (the actual-owner probe establishes that); replacing them with logging/no-op/compatibility stubs is not defensible. This is a committed blocked WIP handoff, not a completion checkpoint disguised as acceptance.
