package mekanism.fabric.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class MekanismNetworkProtocolTest {
    @Test
    void canonicalProtocolMatchesOfficialThreePartVersion() {
        assertEquals("10.7.19", MekanismNetworkProtocol.CURRENT);
        assertTrue(MekanismNetworkProtocol.checkRemote("10.7.19").compatible());
    }

    @Test
    void compatibilityIsExactAndRejectsArtifactOrAlternateVersions() {
        for (String remote : List.of("10.7.18", "10.8.19", "11.7.19", "10:7:19", "10.7.19.85",
              "10.7.19.85-fabric-bootstrap", "", "invalid")) {
            MekanismNetworkProtocol.Compatibility compatibility = MekanismNetworkProtocol.checkRemote(remote);
            assertFalse(compatibility.compatible(), remote);
            assertEquals("Mekanism protocol mismatch: local 10.7.19, remote " + remote, compatibility.disconnectReason());
        }
    }

    @Test
    void peerControlledProtocolIsSanitizedForSingleLineLogging() {
        assertEquals("safe-10.7.19", MekanismNetworkProtocol.sanitizeForLog("safe-10.7.19"));
        assertEquals("bad??tab?null?line?paragraph?", MekanismNetworkProtocol.sanitizeForLog(
              "bad\r\ntab\tnull\0line\u2028paragraph\u2029"));
        assertEquals("emoji-\uD83D\uDE80", MekanismNetworkProtocol.sanitizeForLog("emoji-\uD83D\uDE80"));
        assertThrows(NullPointerException.class, () -> MekanismNetworkProtocol.sanitizeForLog(null));
    }

    @Test
    void handshakeCanResolveOnlyOnce() {
        MekanismNetworkProtocol.Handshake handshake = new MekanismNetworkProtocol.Handshake();
        assertFalse(handshake.isResolved());
        assertTrue(handshake.acknowledge(MekanismNetworkProtocol.CURRENT, true).compatible());
        assertTrue(handshake.isResolved());
        assertThrows(IllegalStateException.class,
              () -> handshake.acknowledge(MekanismNetworkProtocol.CURRENT, true));
    }

    @Test
    void clientRejectionCannotCompleteHandshake() {
        MekanismNetworkProtocol.Handshake handshake = new MekanismNetworkProtocol.Handshake();
        MekanismNetworkProtocol.Compatibility compatibility = handshake.acknowledge(MekanismNetworkProtocol.CURRENT, false);
        assertFalse(compatibility.compatible());
        assertEquals("Remote client rejected Mekanism protocol 10.7.19", compatibility.disconnectReason());
    }

    @Test
    void trackerIsolatesConnectionsAndRejectsDuplicateOrUnexpectedResponses() {
        MekanismNetworkProtocol.HandshakeTracker<String> tracker = new MekanismNetworkProtocol.HandshakeTracker<>();
        assertTrue(tracker.begin("first"));
        assertFalse(tracker.begin("first"));
        assertTrue(tracker.begin("second"));
        assertEquals(2, tracker.pendingCount());

        assertTrue(tracker.acknowledge("first", MekanismNetworkProtocol.CURRENT, true).orElseThrow().compatible());
        assertTrue(tracker.acknowledge("first", MekanismNetworkProtocol.CURRENT, true).isEmpty());
        assertEquals(1, tracker.pendingCount());

        MekanismNetworkProtocol.Compatibility mismatch = tracker.acknowledge("second", "10.7.18", true).orElseThrow();
        assertFalse(mismatch.compatible());
        assertEquals(0, tracker.pendingCount());
        assertTrue(tracker.acknowledge("unknown", MekanismNetworkProtocol.CURRENT, true).isEmpty());

        assertTrue(tracker.begin("disconnecting"));
        assertTrue(tracker.disconnect("disconnecting"));
        assertFalse(tracker.disconnect("disconnecting"));
        assertEquals(0, tracker.pendingCount());
    }

    @Test
    void configurationPayloadCodecsRoundTrip() {
        FriendlyByteBuf requestBuffer = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf responseBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FabricMekanismNetworking.VersionRequest request = new FabricMekanismNetworking.VersionRequest(MekanismNetworkProtocol.CURRENT);
            FabricMekanismNetworking.REQUEST_CODEC.encode(requestBuffer, request);
            assertEquals(request, FabricMekanismNetworking.REQUEST_CODEC.decode(requestBuffer));

            FabricMekanismNetworking.VersionResponse response = new FabricMekanismNetworking.VersionResponse(MekanismNetworkProtocol.CURRENT, true);
            FabricMekanismNetworking.RESPONSE_CODEC.encode(responseBuffer, response);
            assertEquals(response, FabricMekanismNetworking.RESPONSE_CODEC.decode(responseBuffer));
        } finally {
            requestBuffer.release();
            responseBuffer.release();
        }
    }

    @Test
    void versionCodecEnforcesItsBoundAndResponseCodecRejectsTruncation() {
        FriendlyByteBuf boundary = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf oversizedWire = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf truncatedResponse = new FriendlyByteBuf(Unpooled.buffer());
        try {
            String maximum = "x".repeat(MekanismNetworkProtocol.MAX_VERSION_LENGTH);
            FabricMekanismNetworking.VersionRequest boundaryRequest = new FabricMekanismNetworking.VersionRequest(maximum);
            FabricMekanismNetworking.REQUEST_CODEC.encode(boundary, boundaryRequest);
            assertEquals(boundaryRequest, FabricMekanismNetworking.REQUEST_CODEC.decode(boundary));

            FabricMekanismNetworking.VersionRequest oversizedRequest = new FabricMekanismNetworking.VersionRequest(maximum + "x");
            assertThrows(EncoderException.class,
                  () -> FabricMekanismNetworking.REQUEST_CODEC.encode(oversized, oversizedRequest));

            oversizedWire.writeUtf(maximum + "x");
            assertThrows(DecoderException.class, () -> FabricMekanismNetworking.REQUEST_CODEC.decode(oversizedWire));

            truncatedResponse.writeUtf(MekanismNetworkProtocol.CURRENT, MekanismNetworkProtocol.MAX_VERSION_LENGTH);
            assertThrows(IndexOutOfBoundsException.class,
                  () -> FabricMekanismNetworking.RESPONSE_CODEC.decode(truncatedResponse));
        } finally {
            boundary.release();
            oversized.release();
            oversizedWire.release();
            truncatedResponse.release();
        }
    }

    @Test
    void payloadIdsAreStableAndDirectional() {
        assertEquals("mekanism:network_version", FabricMekanismNetworking.REQUEST_ID.toString());
        assertEquals("mekanism:network_version_response", FabricMekanismNetworking.RESPONSE_ID.toString());
    }
}
