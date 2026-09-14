# Task052 review15 — renderer partial delivery 570954d5

Parent reviewed complete new Java diff 29305d9d..570954d5 (geometry/parser, baker, consumer, snapshot/producer) and shipped check. Preserved on pi-agent-d828861e-5654-442; NOT merged/accepted. Task051 remains last accepted. OBJ+baker subset compilation and asset check are child evidence only; consumer/producer not compiled. Broad diagnostic failures do not establish unchanged-baseline equivalence.

Concrete repairs before continuation10 acceptance:
- Consumer emitQuads only calls emitter.fromVanilla; must actually emit each quad and assign proper cutout/translucent RenderMaterial, tint/face metadata. Implements FabricBakedModel alone, not vanilla BakedModel; actual model-loading return owner must be a real BakedModel preserving transforms/default/item behavior.
- Baker uses sprite.getU(u*16)/getV(v*16). Pinned MC TextureAtlasSprite source lines68/86 interpolates normalized 0..1 input; remove erroneous factor16. Rotated geometry must also rotate normal/direction, preserve normal packing/shading semantics.
- Parser strips '#' from alias while consumer adjustAlias compares '#side'/'#center'; normalize consistently so opaque/icon remapping actually executes.
- Geometry maps wrap only outer map; group lists and Face.vertices remain mutable. Snapshot getter exposes mutable connections before freeze, allowing retained reference to mutate after freeze. Deep immutable geometry and never expose mutable snapshot map.
- Producer still retains NeoForge ModelData/property/provider; contract explicitly requires actual replacement, not coexistence as final result. Actual registration plugin, inherited JSON textures, tint provider and reload/config/color-update lifecycle remain unfinished.
- Remove accidentally committed javac.20260914_134426.args and make asset check reproducible (public class filename mismatch currently requires external rename).

Owner decision: TransmitterModelData.check's sole EnumUtils.DIRECTIONS use may be replaced by cached Direction.values() preserving enum ordering. This is a minimal real dependency reduction, not a new ownership boundary or a reason to stop. Admit audited direct model/data/ConnectionType prerequisites; do not chase QIO solely through EnumUtils. Keep one canonical snapshot type. Root registration/container prerequisites remain separately pending.

Continue implementation within CONTINUATION10. Progress checkpoint is not completion or reason to stop 14 minutes early. Time-based model rule still requires safe committed handoff BEFORE14:00 UTC+8; no full fixture/test expansion. Few focused checks for the concrete defects above plus actual-source compile evidence. No runtime claims.
