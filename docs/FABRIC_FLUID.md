# Fabric fluid value and exact-unit gateway

## Task 034 scope

Task 034 is a deliberately bounded two-source platform gateway. It landed with
API **57** / common **122** / platform **17** sources. Tasks 035–036 leave both Task 034
production sources unchanged and bring the current slices to **64/122/17**. The
common slice remains byte-for-byte unchanged. The only Task 034 production sources are:

- `src/api/java/mekanism/api/fluid/FluidStack.java`
- `fabric/src/main/java/mekanism/fabric/fluid/FabricFluidConversions.java`

`mekanism.api.fluid.FluidStack` is a Mekanism-owned value with mutable signed
`int` millibucket (mB) state. Fabric `FluidVariant` and Fabric Transfer units occur
only in `mekanism.fabric.fluid`. Nothing is added under `net.neoforged.*`.

Task 034 itself did **not** replace any existing NeoForge `FluidStack` import. Of
the complete scout inventory of 197 progressive replacements, Task 035 later changes
`SerializerHelper` and Task 036 changes only `TextComponentUtil`; Task 042A changes the fourteen
recipe/`vanilla_input` sources, leaving **181** for future bounded closures. Admitting the value does not
broadly admit callers.

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
- vanilla `DataComponentHolder`; NeoForge's nominal
  `MutableDataComponentHolder` parent is unavailable, but callable parity is restored
  with direct and `Supplier` `set`/`remove`, both direct/`Supplier` `update`
  families, both direct/`Supplier` varargs `copyFrom` families, and both
  `applyComponents` overloads;
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

Component copies own independent `PatchedDataComponentMap` instances. Each
`update` overload delegates through `getOrDefault` and `set`; each `copyFrom`
overload passes the source value (including `null`) to `set`, so a target component
is removed when the source lacks it. Component comparison is structural only in
the explicit static/deprecated helpers. `Object.equals` and `Object.hashCode`
deliberately remain identity-based; no structural overrides are added.

### Block-derived descriptions and omitted methods

Fabric has no NeoForge `FluidType` contract. Task 034 uses the vanilla block
represented by the fluid's default state:

```java
getFluid().defaultFluidState().createLegacyBlock().getBlock().getDescriptionId()
Component.translatable(getDescriptionId())
```

for `getDescriptionId()` and `getHoverName()`. Tests bind water, lava, and empty
to `block.minecraft.water`, `block.minecraft.lava`, and `block.minecraft.air`,
and bind flowing water as a representative built-in block-fluid case without a
synthetic registry fixture. The exact pinned NeoForge registrations assign the
same explicit water/lava IDs, so built-in behavior has parity rather than the
previously documented false `fluid.*` delta. The remaining adaptation is nominal:
Fabric has no `FluidType`, and a custom NeoForge `FluidType` description override
is therefore unavailable; the Fabric convention is block-derived.

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

## Task 035 shared serialization owner

Task 035 admits `mekanism.api.SerializerHelper` and replaces only its NeoForge
`FluidStack` import with this project-owned value. `LENIENT_OPTIONAL_FLUID_CODEC`
therefore retains the official `FluidStack.OPTIONAL_CODEC.promotePartial(...).orElse(FluidStack.EMPTY)`
behavior while using the Fabric-port domain type directly. Valid component-bearing
values and canonical `{}` empty values round-trip; malformed values fall back to the
canonical empty singleton.

The only non-import adaptation avoids eagerly initializing the unfinished
`MekanismAPI` facade: both item/fluid diagnostics use one private SLF4J logger named
exactly `mekanism.api.MekanismAPI`, at error level, with the unchanged message
patterns `Tried to load invalid item: '{}'` and `Tried to load invalid fluid: '{}'`.
This preserves the public diagnostic category without loading `MekanismAPI.class`.
`SerializerHelper` itself is not referenced from a Fabric entrypoint; callers/tests
must initialize vanilla registries before first touching its eager static codec
family. No fluid registry, handler, storage, transaction, lookup, provider, cache,
or lifecycle owner is added.

## Task 036 portable text consumer

Task 036 replaces only `TextComponentUtil`'s NeoForge FluidStack import with this
project-owned value. Its owned-stack path still calls `getHoverName()`. Both raw
`Fluid` paths now construct a translatable component from the exact same convention:

```java
fluid.defaultFluidState().createLegacyBlock().getBlock().getDescriptionId()
```

Dispatch order and smart-translation formatting remain unchanged. Fabric has no
NeoForge FluidType facade, so raw custom `FluidType#getDescription()` overrides are
not observable; Task 036 deliberately does not invent a facade or custom-name table.
Built-in/block-backed names remain block-derived. This is a text prerequisite only,
not fluid storage, transfer, transaction, provider, cache, or owner integration.

## Test and package boundary

Task 034 added exactly two test classes and thirteen `@Test` methods:

- ordinary `FluidStackTest`: nine methods covering emptiness/copies, raw amount
  families, component mutation/copy independence, every restored component
  overload family (including absent-source clearing), holder/tag/predicate/identity
  helpers, JSON codecs, registry-aware NBT, exact stream bytes/errors, and
  block-derived description/deprecated/omitted surfaces;
- Knot `FabricFluidConversionsTest`: four methods covering source water/lava,
  components/blank, flowing rejection, exact factor/boundaries/failures, and a
  deterministic 100,000-case amount loop with 4,096 value round trips.

The ordinary runtime remains free of `fabric-loader-junit` and retains all three
jqwik properties. Task 033's unchanged item transaction suite and the Task 034 fluid
conversion suite share one `fabricTransferTest` source set/task, so a full transfer
run starts Knot once. Task 035 adds one ordinary five-method serialization suite; its landing totals were
ordinary **65 suites / 310 tests**, transfer **2 suites / 8 tests**, aggregate
**67 suites / 318 tests**. Task 036 adds one ordinary five-method text/color suite;
current totals are **66/315**, **2/8**, and **68/323**.

The remapped-JAR static gate requires exactly one
`mekanism/api/fluid/FluidStack.class` and exactly one
`mekanism/fabric/fluid/FabricFluidConversions.class`; lambdas/private methods
avoid incidental nested production classes. Task 035 additionally requires exactly
`SerializerHelper.class` plus its three verified anonymous classes (`$1`, `$2`, `$3`),
all once, and rejects `MekanismAPI.class`, same-prefix extras, NeoForge/Loader bytecode
references, and any serialization startup marker/reference. Task 036 further fixes the
actual compiled `TextComponentUtil.class` + `$1` family and rejects deferred fluid/chemical
classes plus any text startup marker/reference.
`FABRIC_SMOKE_STATIC_ONLY=1 scripts/fabric-packaged-smoke.sh` runs these pre-server gates
without downloading or starting a server. There is no fluid, serialization, or text startup marker.

## Deliberately deferred

Task 034 did not admit `SerializerHelper`; Task 035 admits that shared owner, and
Task 036 admits only the portable TextComponentUtil closure while retaining local,
non-escaping predicates instead of full `ConstantPredicates`. The following remain deferred:

- full `ConstantPredicates`, `ChemicalStack`, and all other translation/predicate callers; Task 042A admits the project `TriPredicate` for `PressurizedReactionRecipe`;
- the remaining **181** progressive NeoForge `FluidStack` import replacements;
- fluid types/attributes or custom FluidType description overrides;
- fluid tanks, handlers, storage adapters, transactions, lookup/provider/cache
  owners, listeners, or lifecycle wiring;
- Task 033's deferred item concrete slots or item provider/cache owner;
- recipes, ingredients, `InputHelper`, `OutputHelper`, or `CachedRecipe`;
- `TileEntityMekanism`, Crusher, any other machine, menu, or client sync;
- persistence owners, packet integration, thread ownership, or concurrency.

This is a value/unit seam only. It makes no transfer acceptance, rollback,
atomicity, remainder ownership, storage capacity, or transaction guarantee.
