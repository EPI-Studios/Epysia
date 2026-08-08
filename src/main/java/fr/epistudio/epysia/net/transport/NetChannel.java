package fr.epistudio.epysia.net.transport;

import java.util.Optional;

public enum NetChannel {
    RELIABLE,
    UNRELIABLE,
    VOICE;

    private static final NetChannel[] VALUES = values();

    public static Optional<NetChannel> fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) {
            return Optional.empty();
        }
        return Optional.of(VALUES[ordinal]);
    }
}
