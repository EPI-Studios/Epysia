package fr.epistudio.epysia.net.session;

import fr.epistudio.epysia.net.protocol.NetReader;
import fr.epistudio.epysia.net.protocol.NetWriter;
import fr.epistudio.epysia.net.protocol.ProtocolVersion;

import java.util.Optional;

public record SessionIdentity(int protocolVersion, int replicationHash, int remoteProcedureHash,
                              String voiceCodecIdentity, String displayName, long joinToken,
                              long reconnectToken) {
    private static final int MAXIMUM_DISPLAY_NAME_LENGTH = 32;

    public static SessionIdentity of(int replicationHash, int remoteProcedureHash,
                                     String voiceCodecIdentity, String displayName, String joinSecret) {
        return new SessionIdentity(ProtocolVersion.CURRENT, replicationHash, remoteProcedureHash,
                voiceCodecIdentity, displayName, JoinToken.of(joinSecret), 0L);
    }

    public void write(NetWriter writer) {
        writer.writeInt(protocolVersion);
        writer.writeInt(replicationHash);
        writer.writeInt(remoteProcedureHash);
        writer.writeString(voiceCodecIdentity);
        writer.writeString(sanitised(displayName));
        writer.writeLong(joinToken);
        writer.writeLong(reconnectToken);
    }

    public static SessionIdentity read(NetReader reader) {
        return new SessionIdentity(reader.readInt(), reader.readInt(), reader.readInt(),
                reader.readString(), sanitised(reader.readString()), reader.readLong(), reader.readLong());
    }

    private static String sanitised(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            return "player";
        }
        return trimmed.length() <= MAXIMUM_DISPLAY_NAME_LENGTH
                ? trimmed
                : trimmed.substring(0, MAXIMUM_DISPLAY_NAME_LENGTH);
    }

    public SessionIdentity withReconnectToken(long token) {
        return new SessionIdentity(protocolVersion, replicationHash, remoteProcedureHash,
                voiceCodecIdentity, displayName, joinToken, token);
    }

    public Optional<DisconnectReason> incompatibilityWith(SessionIdentity other) {
        if (protocolVersion != other.protocolVersion) {
            return Optional.of(DisconnectReason.PROTOCOL_VERSION_MISMATCH);
        }
        if (replicationHash != other.replicationHash) {
            return Optional.of(DisconnectReason.REPLICATION_TABLE_MISMATCH);
        }
        if (remoteProcedureHash != other.remoteProcedureHash) {
            return Optional.of(DisconnectReason.REMOTE_PROCEDURE_TABLE_MISMATCH);
        }
        if (!voiceCodecIdentity.equals(other.voiceCodecIdentity)) {
            return Optional.of(DisconnectReason.VOICE_CODEC_MISMATCH);
        }
        if (joinToken != other.joinToken) {
            return Optional.of(DisconnectReason.JOIN_SECRET_MISMATCH);
        }
        return Optional.empty();
    }
}
