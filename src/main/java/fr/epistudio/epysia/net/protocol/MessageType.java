package fr.epistudio.epysia.net.protocol;

import java.util.Optional;

public enum MessageType {
    CONNECT(1),
    CONNECT_ACCEPTED(2),
    CONNECT_CHALLENGE(13),
    CONNECT_CONFIRM(14),
    CONNECT_REFUSED(3),
    DISCONNECT(4),
    SNAPSHOT(5),
    INPUT_BATCH(6),
    SPAWN(7),
    DESPAWN(8),
    RPC(9),
    ACK(10),
    VOICE(11),
    HEARTBEAT(12),
    PEER_ROSTER(15);

    private static final MessageType[] BY_CODE = buildLookupTable();

    private final int code;

    MessageType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Optional<MessageType> fromCode(int code) {
        if (code < 0 || code >= BY_CODE.length) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CODE[code]);
    }

    private static MessageType[] buildLookupTable() {
        int highest = 0;
        for (MessageType type : values()) {
            highest = Math.max(highest, type.code);
        }
        MessageType[] table = new MessageType[highest + 1];
        for (MessageType type : values()) {
            table[type.code] = type;
        }
        return table;
    }
}
