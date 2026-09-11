# Fabric configuration networking contract

## Current slice

The first networking slice is a mandatory, Mekanism-owned protocol negotiation during Minecraft's configuration phase. It deliberately does not import the official `BasePacketHandler`, `PacketHandler`, `IMekanismPacket`, or packet catalog: those classes still close over NeoForge registration/context APIs and large gameplay/client subsystems.

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

Fabric configuration receivers execute on the Netty event loop. This handshake only compares immutable values, replies, disconnects, and completes its own task; it does not mutate game state. Future handlers that touch server/client state must explicitly schedule onto `MinecraftServer`/`Minecraft`.

## Side and class-loading boundary

```text
MekanismFabricBootstrap
  -> FabricMekanismNetworking             (common/server-safe)

MekanismFabricClientBootstrap
  -> FabricMekanismClientNetworking       (client API only)
```

The common bootstrap has no static reference to the client bootstrap or client networking adapter. The packaged dedicated-server smoke checks both classes are present in the final JAR while proving that the client entrypoint is not loaded on a dedicated server.

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

The dedicated-server smoke cannot exercise a client response, and the unit tests do not instantiate Fabric/Minecraft network handlers. Consequently the real `canSend` rejection, task enqueue/start/complete calls, and handler disconnect paths are compile-checked and reviewed but not yet executed automatically. A paired clean Loader client/server gate is still required to prove those branches, matching join, mismatch disconnect, callback thread behavior, and reconnect state. The current task has no Mekanism-specific timeout; a peer that advertises the channel and then withholds its response remains subject to the base connection lifecycle until that paired gate defines and verifies an explicit policy.

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
