package fr.epistudio.epysia.net.security;

import fr.epistudio.epysia.exceptions.EpysiaException;

import javax.crypto.KeyAgreement;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;

public final class HandshakeKeys {
    public static final int NONCE_BYTES = 16;
    private static final int MAXIMUM_ENCODED_KEY_BYTES = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeyPair keyPair;
    private final byte[] nonce = new byte[NONCE_BYTES];

    private HandshakeKeys(KeyPair keyPair) {
        this.keyPair = keyPair;
        RANDOM.nextBytes(nonce);
    }

    public static HandshakeKeys generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
            generator.initialize(NamedParameterSpec.X25519);
            return new HandshakeKeys(generator.generateKeyPair());
        } catch (GeneralSecurityException failure) {
            throw new EpysiaException("X25519 is unavailable on this platform", failure);
        }
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public byte[] publicKeyBytes() {
        return keyPair.getPublic().getEncoded();
    }

    public byte[] agree(byte[] remotePublicKey) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("X25519");
            agreement.init(keyPair.getPrivate());
            agreement.doPhase(decode(remotePublicKey), true);
            return agreement.generateSecret();
        } catch (GeneralSecurityException | RuntimeException failure) {
            throw new EpysiaException("X25519 key agreement failed", failure);
        }
    }

    public static boolean isPlausiblePublicKey(byte[] encoded) {
        return encoded.length > 0 && encoded.length <= MAXIMUM_ENCODED_KEY_BYTES;
    }

    private static PublicKey decode(byte[] encoded) throws GeneralSecurityException {
        return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(encoded));
    }
}
