# Packaged Fabric server smoke test

Run the final, remapped JAR in a clean dedicated server rather than Loom's development classpath:

```bash
scripts/fabric-packaged-smoke.sh
```

The script reads the pinned Minecraft, Loader, Fabric API, and Fabric Installer versions from `fabric/gradle.properties`, builds `remapJar`, downloads reusable dependencies into `${XDG_CACHE_HOME:-~/.cache}/mekanism-fabric-smoke`, and creates an EULA-accepted server only under a temporary directory. It rejects a mod JAR containing `net/neoforged/` or integration-fixture classes; verifies exact common/client entrypoint metadata and the single intended remapped access-widener entry plus packaged networking, energy-container, basic and variable heat-capacitor, relative-side, holder, and unchanged `WildcardMatcher` classes. It also requires exactly one entry each for `EmitUtils`, `SplitInfo`, `IntegerSplitInfo`, `LongSplitInfo`, `Target`, and `Target$HandlerType`, while rejecting every other class under the distribution package so official/local test and helper classes cannot leak into the release JAR. It passes only after the server log lists mod id `mekanism`, records the clean `values=12` startup-config proof, records configuration-network registration for exact protocol `10.7.19`, parses `config/Mekanism/startup.toml` and compares all paths/defaults exactly, records the `entries=4` canonical proof for all official Mekanism GameEvents, invokes the access-widened position-adjusted biome-temperature path and records its finite-result heat proof, and reports `Done (`. The server process and temporary directory are removed on every exit.

Diagnostic logs are retained under `build/fabric-packaged-smoke/<UTC timestamp>-<pid>/`. Set `FABRIC_SMOKE_KEEP_SERVER=1` to retain the disposable server directory as well. Other useful overrides are `FABRIC_SMOKE_TIMEOUT`, `FABRIC_SMOKE_CACHE_DIR`, `FABRIC_SMOKE_LOG_DIR`, and `FABRIC_INSTALLER_VERSION`.

The networking marker proves codec/server-receiver registration and dedicated-server class-loading safety. Because this disposable server has no connecting client, it does **not** prove an actual configuration payload round trip. Matching round-trip across two sequential fresh client JVMs is covered separately by `scripts/fabric-paired-network-smoke.sh`; incompatible-version, same-version explicit client rejection, and missing-protocol rejection are covered by `scripts/fabric-configuration-rejection-smoke.sh`. Remaining network boundaries are listed in `FABRIC_NETWORK.md`.

Distribution has deliberately **no startup marker**: packaging proves only class presence and test/helper isolation. Exact int/long split, rebalancing, remainder/fallback order, and handler-contract behavior belong to `fabric test`, which compiles the unchanged 15-file official oracle through an explicit allowlist and adds a Fabric-local boundary suite. This utility admission does not prove or include transport callers, Fabric transactions, or machine/network ownership.

This gate performs no Docker, deployment queue, or production-server operations.
