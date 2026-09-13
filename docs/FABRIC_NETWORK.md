# Fabric configuration and Chemical synchronization contract

## Current slice

The first networking slice is a mandatory, Mekanism-owned protocol negotiation during Minecraft's configuration phase. Task 040 adds an ordered Chemical snapshot/ACK task and play reload publication after that compatibility owner. Neither slice imports the official `BasePacketHandler`, `PacketHandler`, `IMekanismPacket`, or packet catalog: those classes still close over NeoForge registration/context APIs and large gameplay/client subsystems.

The observable protocol version is exactly `10.7.19`, matching official Mekanism `Version#toString()` for artifact `10.7.19.85`. The fourth artifact component and the Fabric build suffix are not wire-protocol components. Compatibility is exact for this first slice.

Payloads use configuration `FriendlyByteBuf` codecs:

| Direction | ID | Fields |
|---|---|---|
| server to client | `mekanism:network_version` | bounded protocol-version string |
| client to server | `mekanism:network_version_response` | bounded protocol-version string, accepted boolean |

## Connection flow

1. Common initialization registers both payload codecs, the server response receiver, and server configuration callbacks.
2. Client initialization is isolated in `MekanismFabricClientBootstrap` and registers only the client request receiver.
3. During `ServerConfigurationConnectionEvents.CONFIGURE`, the server rejects clients that did not advertise the request receiver.
4. The server queues a Fabric `ConfigurationTask` keyed by `mekanism:network_version`. The task records per-handler pending state and sends the request through the task-provided packet consumer.
5. The client compares the server protocol with `10.7.19` and replies with its own protocol plus the acceptance result.
6. The server rejects mismatches/rejections. Only an exact accepted response removes pending state and calls Fabric's `completeTask`; play cannot begin through this task before negotiation succeeds.
7. Disconnect cleanup removes pending state. Duplicate or out-of-phase responses are rejected instead of completing an unrelated task.

Fabric configuration receivers execute on the Netty event loop. The version handshake only compares immutable values, replies, disconnects, and completes its own task; it does not mutate game state. Task 040 handlers explicitly schedule server/client state access onto `MinecraftServer`/`Minecraft`.

## Task 040 Chemical snapshot protocol

| Phase/direction | ID | Envelope |
|---|---|---|
| configuration S2C | `mekanism:chemical_snapshot` | session, epoch, three policy fields, five map entry lists |
| configuration C2S | `mekanism:chemical_snapshot_ack` | exact session and epoch |
| play S2C | `mekanism:chemical_snapshot` | a successful replacement epoch for the same session |

After the version callback, the Chemical callback records one per-handler pending object and adds its
own ordered `ConfigurationTask`. If the peer lacks the Chemical receiver, that task emits the Chemical
support rejection; if it lacks the older protocol receiver, the earlier owner always retains the
canonical rejection regardless of any Chemical channel advertisement. The task schedules onto the server executor, snapshots the matching
`FabricChemicalDataMapSession`, sends through the task consumer, and waits. Only an exact positive
session/epoch ACK removes that pending identity, binds player UUID→session and completes this task.
Unexpected, stale, duplicate or malformed ACKs disconnect. Disconnect and server stop remove pending/
play state.

Application snapshots never use registry raw IDs. Map keys are bounded resource names; coolant
references are names in the descriptor codec. Each map value is encoded exactly through its
`MekanismDataMapType.networkCodec()` into one opaque length-delimited chunk. Bounds are session 64
characters, key 128 characters, 65 entries per map, 325 total entries, 256 bytes per value and 65,536
value bytes total. The Netty decoder allocates only these bounded names/chunks. The client executor
then validates registry names, invokes the same five registry-aware descriptor codecs, rejects unknown
references, duplicate keys and trailing bytes, constructs one immutable context, and publishes only a
positive strictly increasing epoch.

Configuration publication replies with ACK only after success. Current-session resolve/publish
failure disconnects. Play reload uses the same fail-closed handler, but captures a client generation:
a task queued before disconnect that runs after reconnect can neither publish into nor disconnect the
replacement connection. Disconnect increments generation and clears session/epoch/context; every
configuration creates a new server session. Successful data reload broadcasts; failed reload retains
the previous server/client context and sends nothing.

`scripts/fabric-chemical-runtime-smoke.sh` runs one real dedicated server and one physical Xvfb client
that disconnects/reconnects in the same JVM. It requires two distinct server ACK sessions (epoch 1,
then epoch 2), initial and removed attribute/tooltips/tank policy, owner-executor unknown-name
rejection, context cleanup, and same-JVM owner isolation. `ChemicalSnapshotProtocolTest` has five
methods for exact descriptor-codec round trip/bounds, unknown/duplicate rejection, monotonic sessions,
and queued-generation fail-closed behavior. Details are in `FABRIC_CHEMICAL.md`.

## Side and class-loading boundary

```text
MekanismFabricBootstrap
  -> FabricMekanismNetworking             (version, common/server-safe)
  -> FabricChemicalNetworking             (snapshot/ACK/play server owner)

MekanismFabricClientBootstrap
  -> FabricMekanismClientNetworking       (version receiver, client API only)
  -> FabricChemicalClientNetworking       (snapshot receiver, client API only)
```

The common bootstrap has no static reference to the client bootstrap or client networking adapter. The packaged dedicated-server smoke checks both classes are present in the final JAR while proving that the client entrypoint is not loaded on a dedicated server. Both rejection fixtures live in isolated custom source sets; `remapJar` excludes them, and packaged smoke rejects any `mekanism/fixture/` class leakage.

## Verification

- `fabric/src/test/java/mekanism/fabric/network/MekanismNetworkProtocolTest.java`
  - exact protocol compatibility and deterministic rejection reason;
  - one-use handshake plus concurrent connection isolation, duplicate/late response and disconnect cleanup state;
  - request/response codec round trips, string bounds and truncated payload rejection;
  - stable directional payload IDs.
- `./gradlew -p fabric test`
- `./gradlew -p fabric verifyPlatformSliceNoNeoForge`
- `scripts/fabric-packaged-smoke.sh`
  - final metadata contains separate main/client entrypoints;
  - both common and client network classes are packaged;
  - common networking registration emits the exact proof marker;
  - a clean dedicated server reaches `Done` without linking client classes.
- `scripts/fabric-paired-network-smoke.sh`
  - launches an isolated Loom server and two sequential fresh Xvfb client JVMs through Quick Play;
  - requires each client to advertise its receiver, start and complete protocol `10.7.19` negotiation, and enter play;
  - requires the first disconnect to be observed before launching the second client;
  - pairs structured session IDs and requires every server start/complete marker to report a Netty event-loop thread;
  - removes disposable run directories and retains diagnostic logs.
- `./gradlew -p fabric verifyHandshakeFixtureIsolation`
  - proves both rejection-fixture runtime classpaths exclude the production source-set output and each other where required;
  - validates distinct client-only metadata and forbids production-network imports;
  - proves the registry-only missing-receiver fixture declares no payload API; mirroring the four synchronized GameEvent IDs is the prerequisite that lets it reach capability negotiation.
- `scripts/fabric-configuration-rejection-smoke.sh`
  - starts one production server, then fresh incompatible-version, same-version explicitly rejecting, and missing-protocol-receiver client JVMs;
  - requires exact structured server rejection and per-username disconnect reasons, meaningful within-process marker order, no completed task, no play join, and a still-live server;
  - uses one wire-compatible independent codec in explicit mismatch/reject modes and a registry-only fixture with no Mekanism payload declaration for the missing-receiver case.

Both process gates are Linux-oriented and require `flock`, Java, Python 3, a systemd user manager, `systemd-run`/`systemctl`, `xvfb-run`, and Xvfb. They serialize access to shared Loom outputs, run each Gradle/game tree in a transient user scope for reliable cleanup, retry loopback-port bind races, and apply one overall deadline (`FABRIC_PAIRED_SMOKE_TIMEOUT` or `FABRIC_REJECTION_SMOKE_TIMEOUT`).

The rejection clients are distinct fixture mods and never import or load Mekanism production classes. They independently register the four synchronized GameEvent IDs solely as a registry-only prerequisite: without those IDs, Fabric registry synchronization rejects the client before configuration payload capability can be observed. The payload fixture registers wire-compatible IDs/codecs and either answers `fixture-mismatch-0` or answers `10.7.19` with `accepted=false`; the missing-receiver fixture declares zero payloads. This isolates receiver-capability behavior without claiming that `canSend=false` proves manifest/mod absence.

The paired gate proves matching negotiation for two independent connections. The rejection gate proves incompatible-version, same-version client-declared rejection, and missing-receiver paths, including that no rejected task completes before disconnect. Task 040's Chemical gate separately proves static registration plus disconnect/reconnect in one physical client JVM; it does not turn the old paired gate into same-process evidence. The remaining protocol boundary is withheld-response timeout policy. There is no Mekanism-specific timeout, so a peer that advertises either required channel and withholds its response remains subject to the base connection lifecycle until an explicit policy is defined and verified.

## Semantic references

No reference implementation code was copied. Phase, direction, task ordering, and protocol-version behavior were checked against:

- Mekanism baseline `a00109e4856fd38b9c5b3dd7f22ce4a59cd65a80`
  - `src/main/java/mekanism/common/lib/Version.java`
  - `src/main/java/mekanism/common/network/BasePacketHandler.java`
  - `src/main/java/mekanism/common/network/PacketHandler.java`
  - `src/main/java/mekanism/common/network/to_client/configuration/SyncAllSecurityData.java`
- Kilt `9e8f0d67411f10234bf0d864bd151a6dbcef14b4`
  - `upstream/kilt/src/main/java/xyz/bluspring/kilt/mixin/compat/fabric_api/network/ServerConfigurationNetworkAddonMixin.java`
- Connector `8b27f1ad042aae8037bcc522b321c03fcce1a12a`
  - `upstream/connector/src/mod/java/org/sinytra/connector/mod/mixin/network/StreamCodecMixin.java`
  - `upstream/connector/src/mod/java/org/sinytra/connector/mod/mixin/registries/NetworkRegistryMixin.java`
