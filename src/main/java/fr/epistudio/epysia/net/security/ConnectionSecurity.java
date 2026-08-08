package fr.epistudio.epysia.net.security;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Optional;

public final class ConnectionSecurity {
    private static final String SERVER_LABEL = "server";
    private static final String CLIENT_LABEL = "client";

    private final byte[] joinSecret;
    private final HandshakeKeys localKeys = HandshakeKeys.generate();
    private final ReplayWindow replayWindow = new ReplayWindow();
    private byte[] remotePublicKey = new byte[0];
    private byte[] remoteNonce = new byte[0];
    private SessionKeys sessionKeys;

    public ConnectionSecurity(String joinSecret) {
        this.joinSecret = joinSecret.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] localPublicKey() {
        return localKeys.publicKeyBytes();
    }

    public byte[] localNonce() {
        return localKeys.nonce();
    }

    public void acceptRemoteHello(byte[] publicKey, byte[] nonce) {
        if (!HandshakeKeys.isPlausiblePublicKey(publicKey)) {
            throw new MessageAuthenticationException("handshake carried an implausible public key");
        }
        this.remotePublicKey = publicKey.clone();
        this.remoteNonce = nonce.clone();
    }

    public void establish(boolean clientSide) {
        byte[] transcript = transcript(clientSide);
        sessionKeys = SessionKeys.derive(localKeys.agree(remotePublicKey), transcript, clientSide);
    }

    public byte[] tagFor(boolean serverSide, boolean clientSide) {
        return SessionKeys.authenticationTag(joinSecret, transcript(clientSide),
                serverSide ? SERVER_LABEL : CLIENT_LABEL);
    }

    public boolean verifyTag(byte[] presented, boolean serverSide, boolean clientSide) {
        return MessageDigest.isEqual(tagFor(serverSide, clientSide), presented);
    }

    private byte[] transcript(boolean clientSide) {
        byte[] localPublic = localKeys.publicKeyBytes();
        byte[] localNonce = localKeys.nonce();
        ByteArrayOutputStream transcript = new ByteArrayOutputStream();
        writeAll(transcript, clientSide ? localPublic : remotePublicKey);
        writeAll(transcript, clientSide ? remotePublicKey : localPublic);
        writeAll(transcript, clientSide ? localNonce : remoteNonce);
        writeAll(transcript, clientSide ? remoteNonce : localNonce);
        return transcript.toByteArray();
    }

    private static void writeAll(ByteArrayOutputStream destination, byte[] bytes) {
        destination.write(bytes, 0, bytes.length);
    }

    public boolean established() {
        return sessionKeys != null;
    }

    public byte[] seal(byte[] plaintext) {
        long counter = sessionKeys.nextSendCounter();
        byte[] sealed = sessionKeys.seal(plaintext, counter);
        byte[] framed = new byte[Long.BYTES + sealed.length];
        for (int index = 0; index < Long.BYTES; index++) {
            framed[index] = (byte) (counter >>> (8 * (Long.BYTES - 1 - index)));
        }
        System.arraycopy(sealed, 0, framed, Long.BYTES, sealed.length);
        return framed;
    }

    public Optional<byte[]> open(byte[] framed) {
        if (framed.length <= Long.BYTES + SessionKeys.TAG_BYTES) {
            return Optional.empty();
        }
        long counter = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            counter = (counter << 8) | (framed[index] & 0xFFL);
        }
        if (!replayWindow.accept(counter)) {
            return Optional.empty();
        }
        return Optional.of(sessionKeys.open(Arrays.copyOfRange(framed, Long.BYTES, framed.length), counter));
    }
}
