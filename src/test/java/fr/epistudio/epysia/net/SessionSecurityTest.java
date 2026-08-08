package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.security.HandshakeKeys;
import fr.epistudio.epysia.net.security.MessageAuthenticationException;
import fr.epistudio.epysia.net.security.ReplayWindow;
import fr.epistudio.epysia.net.security.SessionKeys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SessionSecurityTest {
    private static final byte[] TRANSCRIPT = "transcript".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECRET = "join-secret".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MESSAGE = "the quick brown fox".getBytes(StandardCharsets.UTF_8);

    @Test
    void bothSidesOfAKeyExchangeReachTheSameSecret() {
        HandshakeKeys client = HandshakeKeys.generate();
        HandshakeKeys server = HandshakeKeys.generate();
        assertArrayEquals(client.agree(server.publicKeyBytes()), server.agree(client.publicKeyBytes()));
    }

    @Test
    void aSealedMessageOpensOnlyOnTheOtherSide() {
        byte[] shared = sharedSecret();
        SessionKeys clientKeys = SessionKeys.derive(shared, TRANSCRIPT, true);
        SessionKeys serverKeys = SessionKeys.derive(shared, TRANSCRIPT, false);
        long counter = clientKeys.nextSendCounter();
        byte[] sealed = clientKeys.seal(MESSAGE, counter);
        assertFalse(Arrays.equals(MESSAGE, sealed), "the payload should not travel in the clear");
        assertArrayEquals(MESSAGE, serverKeys.open(sealed, counter));
    }

    @Test
    void aTamperedPacketIsRejectedRatherThanDecoded() {
        byte[] shared = sharedSecret();
        SessionKeys clientKeys = SessionKeys.derive(shared, TRANSCRIPT, true);
        SessionKeys serverKeys = SessionKeys.derive(shared, TRANSCRIPT, false);
        long counter = clientKeys.nextSendCounter();
        byte[] sealed = clientKeys.seal(MESSAGE, counter);
        sealed[sealed.length - 1] ^= 0x01;
        assertThrows(MessageAuthenticationException.class, () -> serverKeys.open(sealed, counter));
    }

    @Test
    void aDifferentJoinSecretProducesADifferentTag() {
        byte[] mine = SessionKeys.authenticationTag(SECRET, TRANSCRIPT, "server");
        byte[] theirs = SessionKeys.authenticationTag("other".getBytes(StandardCharsets.UTF_8),
                TRANSCRIPT, "server");
        assertFalse(Arrays.equals(mine, theirs));
        assertFalse(Arrays.equals(mine, SessionKeys.authenticationTag(SECRET, TRANSCRIPT, "client")),
                "the two directions must not share a tag or either side could replay the other's");
    }

    @Test
    void aReplayedCounterIsRefusedButReorderingIsTolerated() {
        ReplayWindow window = new ReplayWindow();
        assertTrue(window.accept(5));
        assertTrue(window.accept(4), "an out of order packet inside the window is still fresh");
        assertFalse(window.accept(5), "a repeat of a seen counter must be refused");
        assertTrue(window.accept(6));
        assertFalse(window.accept(5 - 200), "a counter far behind the window must be refused");
    }

    private static byte[] sharedSecret() {
        HandshakeKeys client = HandshakeKeys.generate();
        HandshakeKeys server = HandshakeKeys.generate();
        return client.agree(server.publicKeyBytes());
    }
}
