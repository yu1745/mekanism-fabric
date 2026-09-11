# Fabric relative-side energy and heat holders

## Current slice

This milestone adds the platform-neutral side-routing layer used by Mekanism's internal holders:

- `mekanism.api.RelativeSide`
- `mekanism.api.text.IHasTranslationKey`
- `mekanism.common.capabilities.holder.IHolder`
- `mekanism.common.capabilities.holder.BasicHolder`
- `mekanism.common.capabilities.holder.energy.IEnergyContainerHolder`
- `mekanism.common.capabilities.holder.energy.EnergyContainerHolder`
- `mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder`
- `mekanism.common.capabilities.holder.heat.HeatCapacitorHolder`

It builds on the internal energy and heat containers but does not expose either storage family through a Fabric block/item lookup.

## Relative-side contract

`RelativeSide` retains its exact FQN and nested consumers continue to use the same enum identity. The following official contracts are preserved:

- constant order: `FRONT`, `LEFT`, `RIGHT`, `BACK`, `TOP`, `BOTTOM`;
- lowercase serialized names;
- `CODEC`, ordinal `STREAM_CODEC`, and wrapping `BY_ID` behavior;
- all horizontal and vertical `getDirection`/`fromDirections` mappings;
- translation keys `side.mekanism.<name>`;
- `IHasEnumNameTranslationKey` nominal identity and `getTranslatedName()` method.

The enum no longer pulls `APILang`, `ILangEntry`, `TextComponentUtil`, or `MekanismAPI` merely to obtain six stable keys. It derives the same vanilla description IDs locally.

`IHasEnumNameTranslationKey` no longer extends NeoForge `TranslatableEnum`. Its Mekanism-visible method now directly returns `Component.translatable(getTranslationKey())`, which is the exact behavior of the former helper call. The removed NeoForge subtype relationship is a deliberate Fabric API boundary and is registered in `API_DELTAS.md`.

## Holder behavior

`BasicHolder` preserves the official two-view model:

- one insertion-ordered master list for internal (`side == null`) access;
- an `EnumMap<RelativeSide, List<T>>` for external side visibility;
- if no side mapping exists anywhere, all physical sides expose the master list without consulting the facing supplier;
- once any mapping exists, external access is filtered at lookup time using the current facing;
- an entry added without sides then remains internal-only;
- shared entries retain object identity and insertion order;
- duplicate side declarations remain duplicate entries;
- populated lists remain live mutable lists, while absent mappings return the immutable empty list.

`IHolder`/`IEnergyContainerHolder` retain unrestricted default `canInsert` and `canExtract` behavior. `HeatCapacitorHolder` applies the same official routing model without adding energy-style permission methods. Configurable holders and helpers remain separate.

## Deliberately deferred

This slice excludes:

- `EnergyContainerHelper` and `HeatCapacitorHelper`;
- `ConfigHolder`, `ConfigEnergyContainerHolder`, and `ConfigHeatCapacitorHolder`;
- `ISideConfiguration` and machine component configuration;
- capability/lookup registration and proxies;
- Team Reborn Energy API and Fabric transactions;
- quantum-entangloporter and other specialized holders.

The energy and heat helpers currently couple their non-config and configurable factories. They must be split or admitted with their real side-configuration closure rather than stubbed.

## Verification

- `fabric/src/test/java/mekanism/api/RelativeSideTest.java`
  - exact order, names, translations, `BY_ID`, data/stream codecs;
  - one fixed 6×6 projection table covering every horizontal and vertical facing;
  - all 36 relative/physical round trips against that table.
- `fabric/src/test/java/mekanism/common/capabilities/holder/energy/EnergyContainerHolderTest.java`
  - unconfigured all-side behavior without facing evaluation;
  - internal ordering and external filtering;
  - live facing rotation;
  - internal-only entries after side configuration;
  - identity, duplicate mapping, mutable live-list and immutable empty-list behavior;
  - unrestricted default permissions.
- `fabric/src/test/java/mekanism/common/capabilities/holder/heat/HeatCapacitorHolderTest.java`
  - exercises representative horizontal and vertical routing for the official heat-holder specialization;
  - verifies internal/all-side identity, filtering, live facing, order, duplicates, and list mutability.

Required gates:

```bash
./gradlew -p fabric test
./gradlew -p fabric verifyApiSliceNoNeoForge verifyCommonSliceNoNeoForge
./fabric-build.sh
scripts/fabric-packaged-smoke.sh
```

The packaged smoke verifies the relative-side translation interface plus energy- and heat-holder classes are present in the remapped JAR. Directional behavior is exercised in JUnit and does not create synthetic holder state during normal mod bootstrap.
