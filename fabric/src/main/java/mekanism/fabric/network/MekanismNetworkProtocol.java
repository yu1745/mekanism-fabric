package mekanism.fabric.network;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Version contract shared by the Fabric configuration-phase handshake. */
public final class MekanismNetworkProtocol {
    /** Matches the official Mekanism {@code Version#toString()} value for 10.7.19.85. */
    public static final String CURRENT = "10.7.19";
    public static final int MAX_VERSION_LENGTH = 32;

    private MekanismNetworkProtocol() {
    }

    static String sanitizeForLog(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder sanitized = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint) || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                sanitized.append('?');
            } else {
                sanitized.appendCodePoint(codePoint);
            }
        });
        return sanitized.toString();
    }

    public static Compatibility checkRemote(String remoteVersion) {
        Objects.requireNonNull(remoteVersion, "remoteVersion");
        if (CURRENT.equals(remoteVersion)) {
            return new Compatibility(true, "");
        }
        return new Compatibility(false, "Mekanism protocol mismatch: local " + CURRENT + ", remote " + remoteVersion);
    }

    /** One-use state for a server-side configuration negotiation. */
    static final class Handshake {
        private boolean resolved;

        synchronized Compatibility acknowledge(String remoteVersion, boolean remoteAccepted) {
            if (resolved) {
                throw new IllegalStateException("Mekanism protocol handshake is already resolved");
            }
            resolved = true;
            Compatibility compatibility = checkRemote(remoteVersion);
            if (!remoteAccepted && compatibility.compatible()) {
                return new Compatibility(false, "Remote client rejected Mekanism protocol " + CURRENT);
            }
            return compatibility;
        }

        synchronized boolean isResolved() {
            return resolved;
        }
    }

    /** Tracks concurrent configuration handshakes without exposing Minecraft connection objects to the state logic. */
    static final class HandshakeTracker<K> {
        private final Map<K, Handshake> pending = new ConcurrentHashMap<>();

        boolean begin(K connection) {
            Objects.requireNonNull(connection, "connection");
            return pending.putIfAbsent(connection, new Handshake()) == null;
        }

        Optional<Compatibility> acknowledge(K connection, String remoteVersion, boolean remoteAccepted) {
            Objects.requireNonNull(connection, "connection");
            Handshake handshake = pending.remove(connection);
            if (handshake == null) {
                return Optional.empty();
            }
            return Optional.of(handshake.acknowledge(remoteVersion, remoteAccepted));
        }

        boolean disconnect(K connection) {
            Objects.requireNonNull(connection, "connection");
            return pending.remove(connection) != null;
        }

        int pendingCount() {
            return pending.size();
        }
    }

    public record Compatibility(boolean compatible, String disconnectReason) {
        public Compatibility {
            Objects.requireNonNull(disconnectReason, "disconnectReason");
            if (compatible && !disconnectReason.isEmpty()) {
                throw new IllegalArgumentException("Compatible protocols cannot have a disconnect reason");
            }
            if (!compatible && disconnectReason.isBlank()) {
                throw new IllegalArgumentException("Incompatible protocols require a disconnect reason");
            }
        }
    }
}
