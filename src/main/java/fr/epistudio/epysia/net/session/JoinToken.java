package fr.epistudio.epysia.net.session;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class JoinToken {
    public static final long ABSENT = 0L;

    private JoinToken() {
    }

    public static long of(String joinSecret) {
        if (joinSecret == null || joinSecret.isEmpty()) {
            return ABSENT;
        }
        byte[] digest = sha256().digest(joinSecret.getBytes(StandardCharsets.UTF_8));
        long token = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            token = (token << 8) | (digest[index] & 0xFFL);
        }
        return token;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new EpysiaException("SHA-256 is unavailable on this platform", missing);
        }
    }
}
