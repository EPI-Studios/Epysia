package fr.epistudio.epysia.net.session;

import java.util.Optional;

public enum DisconnectReason {
    REQUESTED,
    TIMEOUT,
    PROTOCOL_VERSION_MISMATCH,
    REPLICATION_TABLE_MISMATCH,
    REMOTE_PROCEDURE_TABLE_MISMATCH,
    VOICE_CODEC_MISMATCH,
    SERVER_FULL,
    JOIN_SECRET_MISMATCH,
    RATE_LIMIT_EXCEEDED,
    HANDSHAKE_TIMEOUT,
    KICKED,
    BANNED,
    TRANSPORT_CLOSED;

    private static final DisconnectReason[] VALUES = values();

    public static Optional<DisconnectReason> fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) {
            return Optional.empty();
        }
        return Optional.of(VALUES[ordinal]);
    }
}
