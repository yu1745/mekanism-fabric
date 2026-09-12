# Fabric fluid value and exact-unit gateway

## Task 034 scope

Task 034 is a deliberately bounded two-source platform gateway. The audited source
slices are API **57** / common **122** / platform **17**. The common slice is
byte-for-byte unchanged. The only new production sources are:

- `src/api/java/mekanism/api/fluid/FluidStack.java`
- `fabric/src/main/java/mekanism/fabric/fluid/FabricFluidConversions.java`

`mekanism.api.fluid.FluidStack` is a Mekanism-owned value with mutable signed
`int` millibucket (mB) state. Fabric `FluidVariant` and Fabric Transfer units occur
only in `mekanism.fabric.fluid`. Nothing is added under `net.neoforged.*`.

This batch does **not** replace any existing NeoForge `FluidStack` import. In
particular, the complete scout inventory of 197 progressive import replacements is
left for later bounded closures. Admitting the value does not admit a caller.

## Behavioral provenance

The class was independently implemented from the Task 034 behavior contract. The
behavioral oracle inspected for surface and edge semantics was NeoForge
**21.1.200**, Maven coordinate `net.neoforged:neoforge:21.1.200`, source path
`net/neoforged/neoforge/fluids/FluidStack.java`. That reference is
**LGPL-2.1-only** (`SPDX-License-Identifier: LGPL-2.1-only`); no source text was
copied. The inspected source artifact SHA-256 is
`7b31b7f4b817c90b8aa05ade830d761223dff07967aab49ab1f197d51c4d1420`
and the extracted reference file SHA-256 is
`45d52969b9831e51ac9aca526f0a1f3646ccf8ffe8fd1cd7296b7a7ded093601`.

The resolved Fabric dependency graph supplies
`fabric-transfer-api-v1:5.4.4+7b3d111d19`. Its source artifact SHA-256 is
`33a639294d6342f07cdb95824f921af056ec8405ffe54df4cfd966fb2cc4c461`
and is Apache-2.0. The inspected paths were:

- `net/fabricmc/fabric/api/transfer/v1/fluid/FluidConstants.java`;
- `net/fabricmc/fabric/api/transfer/v1/fluid/FluidVariant.java`;
- `net/fabricmc/fabric/impl/transfer/fluid/FluidVariantImpl.java`;
- `net/fabricmc/fabric/impl/transfer/fluid/FluidVariantCache.java`.

They establish `FluidConstants.BUCKET == 81_000` and that
`FluidVariant.of` normalizes a flowing `FlowingFluid` to its source/still fluid.
Task 034 uses those facts only in the Fabric seam.

## `FluidStack` value contract

The public value surface retains the platform-neutral NeoForge/Mekanism contract:

- public canonical `EMPTY`;
- holder/amount/patch, holder/amount, and fluid/amount constructors;
- mutable `int` amount and a `PatchedDataComponentMap` over
  `DataComponentMap.EMPTY`;
- vanilla `DataComponentHolder`, plus public `set`, `remove`, and both
  `applyComponents` overloads because NeoForge's
  `MutableDataComponentHolder` is unavailable;
- holder, fluid, tag, holder-set, and holder-predicate tests plus `getTags`;
- copies, split/clear, amount limits, raw signed growth/shrinkage, matching, and
  fluid/component hashing;
- registry-aware JSON/NBT/stream codecs and the existing misspelled public method
  `lenientOtionalFieldOf`;
- platform-neutral deprecated display/equality/containment methods.

Emptiness is exactly `this == EMPTY`, `fluid == Fluids.EMPTY`, or raw
`amount <= 0`. Empty-observing access returns `Fluids.EMPTY`, amount zero, and an
empty component patch. Constructors and `copyWithAmount` do not eagerly replace a
new nonpositive object with the singleton; copying an already empty stack does.
`split` uses the raw stored amount, and `grow`/`shrink` retain Java `int`
overflow and the empty-observing amount behavior. These are compatibility
semantics, not recommended arithmetic.

Component copies own independent `PatchedDataComponentMap` instances. Component
comparison is structural only in the explicit static/deprecated helpers.
`Object.equals` and `Object.hashCode` deliberately remain identity-based; no
structural overrides are added.

### Description delta and omitted methods

Fabric has no NeoForge `FluidType` contract. Task 034 therefore defines:

```java
Util.makeDescriptionId("fluid", BuiltInRegistries.FLUID.getKey(getFluid()))
Component.translatable(getDescriptionId())
```

for `getDescriptionId()` and `getHoverName()`. Tests bind the resulting built-in
IDs `fluid.minecraft.water` and `fluid.minecraft.lava`. Custom NeoForge
`FluidType` description overrides are intentionally unavailable.

The exact pinned NeoForge registration source also assigns its built-in water and
lava `FluidType` properties the explicit IDs `block.minecraft.water` and
`block.minecraft.lava`. Consequently, the mandated registry-derived Fabric IDs
above are an intentional observable platform delta rather than byte-for-byte IDs
of those two explicit NeoForge registrations. This discrepancy must not be hidden
by a false compatibility claim.

The following NeoForge-only methods are deliberately absent:

- `getFluidType()`;
- `is(FluidType)`;
- deprecated `isFluidEqual(ItemStack)`, which performs a capability lookup.

## Codec and persistence formats

`FLUID_NON_EMPTY_CODEC` is the built-in fluid holder-by-name codec with
`minecraft:empty` rejected as `Fluid must not be minecraft:empty`. Lazy `CODEC`
uses required `id`, required positive `amount` (no default), and optional
`components` defaulting to `DataComponentPatch.EMPTY`. `fixedAmountCodec`, the
`{}`-encoding `OPTIONAL_CODEC`, and `lenientOtionalFieldOf` retain the same field
shapes and failure boundaries.

`OPTIONAL_STREAM_CODEC` writes amount as VAR_INT first. A decoded amount `<= 0`
returns canonical `EMPTY` without reading a holder or patch. A positive value then
uses the fluid registry holder codec and `DataComponentPatch.STREAM_CODEC`. The
strict codec throws exactly `DecoderException`/`EncoderException` with
`Empty FluidStack not allowed`. For built-in water, amount 1,000 and an empty
patch, the tested bytes are `E8 07 02 00 00`; optional empty is `00`.

`parse`, `parseOptional`, `save(provider, prefix)`, `save(provider)`, and
`saveOptional` use the registry-aware vanilla codec context directly. Prefix map
data is merged and preserved. Empty saves throw exactly
`IllegalStateException("Cannot encode empty FluidStack")`. The only removed
NeoForge behavior is diagnostic logging around codec failure; returned
partial/empty results, encoded data, and propagated exceptions remain the codec's
own behavior.

## Fabric value/unit seam

`FabricFluidConversions` exposes only four operations and three constants:

- `millibucketsToTransferUnits(int)`;
- `transferUnitsToMillibucketsExact(long)`;
- `toFabricVariant(FluidStack)`;
- `toFluidStack(FluidVariant, long)`;
- `MILLIBUCKETS_PER_BUCKET == 1_000`;
- `TRANSFER_UNITS_PER_MILLIBUCKET == 81`;
- `MAX_EXACT_TRANSFER_UNITS == 173_946_175_407L`.

Class initialization verifies that `1_000 * 81 == FluidConstants.BUCKET`.
Mekanism-to-Fabric amount conversion rejects negative raw input and uses checked
`long` multiplication. Fabric-to-Mekanism conversion rejects negative values,
any remainder modulo 81 (including every 1..80 sub-mB request), and a quotient
greater than `Integer.MAX_VALUE`; it never floors, rounds, or consumes a hidden
remainder. Zero converts to zero and becomes canonical `EMPTY` when paired with a
variant.

An empty stack maps to `FluidVariant.blank()`. A blank variant with a valid exact
amount maps to canonical `FluidStack.EMPTY`. Unit validity is checked before the
blank shortcut, so malformed negative/nondivisible/out-of-domain requests are
not silently hidden by a blank resource.

A nonblank source/still stack preserves exact fluid identity and
`DataComponentPatch`. Reverse conversion restores the variant patch exactly,
including removal entries that ordinary apply-to-empty semantics would sanitize
away. The seam invokes `FluidVariant.of` and then compares the resulting fluid
identity; normalization of flowing water/lava causes a deterministic
`IllegalArgumentException` instead of a false round-trip claim. Built-in source
water and lava round-trip exactly. No floor/bounded transaction helper is exposed.

## Test and package boundary

Task 034 adds exactly two test classes and twelve `@Test` methods:

- ordinary `FluidStackTest`: eight methods covering emptiness/copies, raw amount
  families, component mutation/copy independence, holder/tag/predicate/identity
  helpers, JSON codecs, registry-aware NBT, exact stream bytes/errors, and
  description/deprecated/omitted surfaces;
- Knot `FabricFluidConversionsTest`: four methods covering source water/lava,
  components/blank, flowing rejection, exact factor/boundaries/failures, and a
  deterministic 100,000-case amount loop with 4,096 value round trips.

The ordinary runtime remains free of `fabric-loader-junit` and retains all three
jqwik properties. Task 033's unchanged item transaction suite and the new fluid
conversion suite share one `fabricTransferTest` source set/task, so a full transfer
run starts Knot once. Expected totals are ordinary **64 suites / 304 tests**,
transfer **2 suites / 8 tests**, aggregate **66 suites / 312 tests**.

The remapped-JAR static gate requires exactly one
`mekanism/api/fluid/FluidStack.class` and exactly one
`mekanism/fabric/fluid/FabricFluidConversions.class`; lambdas/private methods
avoid incidental nested production classes. It rejects unexpected same-prefix
helpers/tests, NeoForge classes, nested Team Energy classes, and Fabric Loader
classes. `FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh` runs these
pre-server gates without downloading or starting a server. There is no fluid
startup marker.

## Deliberately deferred

Task 034 does not admit or adapt:

- `SerializerHelper`, `ConstantPredicates`, `TextComponentUtil`, translation or
  predicate callers;
- any of the 197 progressive import replacements;
- fluid types/attributes or custom description overrides;
- fluid tanks, handlers, storage adapters, transactions, lookup/provider/cache
  owners, listeners, or lifecycle wiring;
- Task 033's deferred item concrete slots or item provider/cache owner;
- recipes, ingredients, `InputHelper`, `OutputHelper`, or `CachedRecipe`;
- `TileEntityMekanism`, Crusher, any other machine, menu, or client sync;
- persistence owners, packet integration, thread ownership, or concurrency.

This is a value/unit seam only. It makes no transfer acceptance, rollback,
atomicity, remainder ownership, storage capacity, or transaction guarantee.
