package fr.epistudio.epysia.net.security;

import fr.epistudio.epysia.exceptions.EpysiaException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public final class SessionKeys {
    public static final int TAG_BYTES = 16;
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int SALT_BYTES = 4;
    private static final String CIPHER = "ChaCha20-Poly1305";
    private static final String MAC = "HmacSHA256";
    private static final byte[] CLIENT_TO_SERVER_INFO = "epysia-net-v1 client-to-server".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SERVER_TO_CLIENT_INFO = "epysia-net-v1 server-to-client".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec sendKey;
    private final SecretKeySpec receiveKey;
    private final byte[] sendSalt;
    private final byte[] receiveSalt;
    private long sendCounter;

    private SessionKeys(byte[] sendMaterial, byte[] receiveMaterial) {
        this.sendKey = new SecretKeySpec(Arrays.copyOf(sendMaterial, KEY_BYTES), "ChaCha20");
        this.receiveKey = new SecretKeySpec(Arrays.copyOf(receiveMaterial, KEY_BYTES), "ChaCha20");
        this.sendSalt = Arrays.copyOfRange(sendMaterial, KEY_BYTES, KEY_BYTES + SALT_BYTES);
        this.receiveSalt = Arrays.copyOfRange(receiveMaterial, KEY_BYTES, KEY_BYTES + SALT_BYTES);
    }

    public static SessionKeys derive(byte[] sharedSecret, byte[] transcript, boolean asClient) {
        byte[] extracted = extract(transcript, sharedSecret);
        byte[] clientToServer = expand(extracted, CLIENT_TO_SERVER_INFO);
        byte[] serverToClient = expand(extracted, SERVER_TO_CLIENT_INFO);
        return asClient
                ? new SessionKeys(clientToServer, serverToClient)
                : new SessionKeys(serverToClient, clientToServer);
    }

    private static byte[] extract(byte[] salt, byte[] material) {
        return hmac(salt, material);
    }

    private static byte[] expand(byte[] pseudoRandomKey, byte[] info) {
        byte[] block = new byte[info.length + 1];
        System.arraycopy(info, 0, block, 0, info.length);
        block[info.length] = 1;
        return hmac(pseudoRandomKey, block);
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(MAC);
            mac.init(new SecretKeySpec(key.length == 0 ? new byte[1] : key, MAC));
            return mac.doFinal(message);
        } catch (GeneralSecurityException failure) {
            throw new EpysiaException("HMAC-SHA256 is unavailable on this platform", failure);
        }
    }

    public static byte[] authenticationTag(byte[] joinSecret, byte[] transcript, String label) {
        byte[] labelled = new byte[transcript.length + label.length()];
        System.arraycopy(transcript, 0, labelled, 0, transcript.length);
        System.arraycopy(label.getBytes(StandardCharsets.UTF_8), 0, labelled, transcript.length, label.length());
        return hmac(joinSecret, labelled);
    }

    public long nextSendCounter() {
        return sendCounter++;
    }

    public byte[] seal(byte[] plaintext, long counter) {
        return transform(Cipher.ENCRYPT_MODE, sendKey, nonceOf(sendSalt, counter), plaintext);
    }

    public byte[] open(byte[] ciphertext, long counter) {
        return transform(Cipher.DECRYPT_MODE, receiveKey, nonceOf(receiveSalt, counter), ciphertext);
    }

    private static byte[] transform(int mode, SecretKeySpec key, byte[] nonce, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(mode, key, new IvParameterSpec(nonce));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException failure) {
            throw new MessageAuthenticationException("packet failed authenticated decryption");
        }
    }

    private static byte[] nonceOf(byte[] salt, long counter) {
        ByteBuffer nonce = ByteBuffer.allocate(NONCE_BYTES);
        nonce.put(salt);
        nonce.putLong(counter);
        return nonce.array();
    }
}
