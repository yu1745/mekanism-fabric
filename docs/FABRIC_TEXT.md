# Portable API text and color closure

> 状态核实（2026-10-30 / W-05）：保留对应早期切片的语义设计与验收记录；文中 “current”、slice 数量及延期调用方仅指当时范围，不代表 Task052 后的纳入状态。当前状态与后续验收以 `milestones/README.md` 和 `milestones/ROADMAP.md` 为准。

## Task 036 scope

Task 036 admits one connected eight-source API closure and changes no common or
Fabric-platform production source, dependency, entrypoint, or runtime owner. The
audited API/common/platform slices are now **64/122/17**:

- newly allowlisted:
  - `mekanism.api.IIncrementalEnum`;
  - `mekanism.api.inventory.IHashedItem`;
  - `mekanism.api.text.APILang`;
  - `mekanism.api.text.EnumColor`;
  - `mekanism.api.text.ILangEntry`;
  - `mekanism.api.text.TextComponentUtil`;
- immediately restored existing callers:
  - `mekanism.api.text.IHasTranslationKey`;
  - `mekanism.api.RelativeSide`.

`IHashedItem`, `EnumColor`, and `ILangEntry` remain byte-identical to oracle
`4646f509`. `RelativeSide` is also restored byte-identically: its six constants
again retain the corresponding `APILang`/`ILangEntry`, and
`getTranslationKey()` delegates to that object. Its enum order, serialized names,
codecs, and direction projections do not change.

This is a portable prerequisite closure only. It does not admit the concrete
`mekanism.common.lib.inventory.HashedItem`, `ConstantPredicates`, `ChemicalStack`,
`TriPredicate`, warnings, menus, concrete inventory slots, recipes, or any
registry/config/lifecycle/transfer/persistence/network owner.

## Bounded source adaptations

Relative to `4646f509`, the production delta is limited to the following:

1. `IIncrementalEnum` removes the `ConstantPredicates` import. Only the two
   no-argument defaults replace `ConstantPredicates.alwaysTrue()` with stateless,
   method-local `ignored -> true` predicates. Every public/default signature and
   traversal/adjustment body is otherwise unchanged; neither predicate is
   returned, retained, or exposed.
2. `APILang` preserves all 57 constants and generated description IDs. Its private
   constructor uses the stable literal namespace `"mekanism"` instead of eagerly
   loading `MekanismAPI.MEKANISM_MODID`. No display text is inlined.
3. `TextComponentUtil` uses the project-owned
   `mekanism.api.fluid.FluidStack`. Both raw-`Fluid` dispatch sites create a
   translatable component from
   `fluid.defaultFluidState().createLegacyBlock().getBlock().getDescriptionId()`,
   the same Task 034 convention used by `FluidStack#getDescriptionId`.
4. Vanilla `Level` lacks NeoForge's injected `ILevelExtension#getDescription()`.
   Its two existing dispatch positions are expanded to that extension's vanilla
   semantics: a `Component.translatableWithFallback` keyed by
   `level.dimension().location().toLanguageKey("dimension")`, with the exact
   dimension ID string as fallback. Dispatch order and fallback behavior remain
   unchanged.
5. `IHasTranslationKey.IHasEnumNameTranslationKey` retains Task 032's approved
   removal of the NeoForge `TranslatableEnum` parent, import, stale override, and
   Javadoc claim. Its default body is restored to
   `TextComponentUtil.translate(getTranslationKey())`.

No public Mekanism method is added, removed, or reordered by this task.
`TextComponentUtil.build` still unwraps only bound holders, skips nulls, applies a
cached style to the next emitted component and then resets it, ignores trailing
formatting, and returns `null` for an empty/all-null input. Its type-dispatch order
is unchanged. `smartTranslate` still emits literal null, cleans both NBSP forms,
preserves allowed primitive arguments, applies only the first pending style of
each type, resets style after one argument, preserves the official trailing-format
fallback, copies component arguments, and retains the official translation-argument
ordering.

### Raw-fluid naming limitation

Built-in and ordinary block-backed fluids use the same block-derived translatable
key through both `FluidStack` and raw `Fluid` paths. Fabric has no NeoForge
`FluidType` facade, so a custom NeoForge `FluidType#getDescription()` override
cannot be observed here. Task 036 deliberately does not invent a fluid-type or
custom-name facade; custom raw-fluid naming is limited to the default fluid
state's legacy block description ID.

## Color and incremental contracts

`EnumColor` retains its 18-value order, section-sign codes, English names,
serialized/registry prefixes, APILang ownership, dye/map mappings, wrapping
`BY_ID`, string `CODEC`, ordinal `STREAM_CODEC`, optional stream codec, and mutable
atlas RGB/TextColor/packed-color state. `IIncrementalEnum` retains positive and
negative wrap, predicate skipping, all-invalid self-return, and signed adjustment
semantics.

`IHashedItem` remains only the official interface and exposes the backing stack,
copy-with-size contract, item, holder, max-stack defaults. No implementation is
admitted.

## Verification and package boundary

One ordinary class,
`fabric/src/test/java/mekanism/api/text/PortableTextColorTest.java`, has exactly
five tests. Vanilla is bootstrapped before the closure is first used. The methods
cover incremental traversal; all APILang keys plus RelativeSide delegation;
EnumColor metadata/codecs/mutable atlas restoration; `build` holder/item/fluid/
style/null ordering; and `smartTranslate` primitive, null/NBSP, formatting,
colored-entry, trailing, and mutation-sensitive argument semantics.
`RelativeSideTest` remains unchanged and separately supplies codec and all 36
direction round trips.

Expected totals are ordinary **66 suites/315 tests**, isolated Knot transfer
**2 suites/8 tests**, aggregate **68 suites/323 tests**. The three unchanged jqwik
properties each retain **100,000** tries/checks.

Mechanical inspection of the actual remapped JAR fixes the new class shape:
`IIncrementalEnum.class`, `IHashedItem.class`, `APILang.class`, `EnumColor.class`,
`ILangEntry.class`, and `TextComponentUtil.class` plus `TextComponentUtil$1.class`.
The complete direct text package also retains exactly the existing
`IHasTextComponent` outer/nested and `IHasTranslationKey` outer/nested families;
`RelativeSide` remains exactly outer plus `$1`. Every entry must occur once.
Static gates reject unexpected same-prefix/direct-package classes, test/helper/
fixture leakage, concrete `HashedItem`, `MekanismAPI`, `ConstantPredicates`, all
chemical API classes, `TriPredicate`, NeoForge/Loader bytecode references, any
Task 036 startup marker, and direct main/client entrypoint references to this
closure.

Task 036 has deliberately no startup proof. Candidate verification ends after
all static remapped-JAR gates and does not start a packaged server.
