# Task052 review16 — c935d377 handoff to CC DeepSeek

Parent read complete 902e98ca..c935d377 diff including evidence. Preserved branch pi-agent-6ff4ac73-3745-4f3, not merged/accepted. Time now14:02 UTC+8; next normal implementer commandcode/deepseek/deepseek-v4.1-flash (exact model resolved by pi --list-models). Previous GLM ended safely13:59.

Verified source repairs: actual emitter.emit plus separate materials; BakedModel implementation; UV normalized and normal rotated; consistent stripped aliases; unmodifiable snapshot view from construction; direct Direction.values dependency trim; producer ModelData path removed; generated javac args deleted. Compile not established; killed diagnostic is not pass. No runtime acceptance.

Still concrete defects, despite child claim all review15 fixes landed:
- Face record's vertices List is still mutable (parser passes ArrayList). Wrapping group list alone is not deep immutability. Defensive List.copyOf in Face constructor (or equivalent actual immutable construction) required.
- getTransforms returns NO_TRANSFORMS with comment discarding JSON transforms, violating explicit original contract. Real loader must propagate inherited JSON transforms/default geometry; do not claim identity is preservation.
- Old official TransmitterBakedModel now sets data=null to keep excluded source syntactically intact. This is prohibited fake/no-op cleanup. Restore excluded reference body to pre-batch behavior unless replacing that real owner completely; absence of its old producer field is an honest migration diagnostic, not permission to neutralize branch.
- Provider in TileEntityTransmitter still NeoForge; existing authorized Fabric BiFunction migration remains needed.
- Asset check header compiles into /tmp/t052c but runs /tmp/t052, also missing annotations classpath. Make actual repeatable command/script, do not claim reproduced from header.

Continue original CONTINUATION10 closure (not a new planning round): actual model plugin loading OBJ/MTL+child texture aliases, baking owner returns new model with inherited transforms, tint registration and packet/config/reload lifecycle; proper renderer access lifecycle and model-owned sprite caches. Canonical snapshot and no unrelated root-owner expansion. Use Loom-resolved/remapped compile classpath, not raw intermediary jars; concrete actual-class diagnostics matter. Normal build + few targeted checks, no full fixture/test inflation. Preserve ancestry c935d377 and prior WIP9cc0a0b8. Task051 last accepted.
