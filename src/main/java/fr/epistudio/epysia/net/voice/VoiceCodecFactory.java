package fr.epistudio.epysia.net.voice;

import fr.epistudio.epysia.logging.Logger;

import java.util.Optional;

public final class VoiceCodecFactory {
    private VoiceCodecFactory() {
    }

    public static Optional<VoiceCodec> create(VoiceConfig config, Logger logger) {
        if (RawPcmVoiceCodec.IDENTITY.equals(config.codecIdentity())) {
            return Optional.of(new RawPcmVoiceCodec());
        }
        Optional<VoiceCodec> opus = OpusVoiceCodec.tryCreate(config.bitrate());
        if (opus.isEmpty()) {
            logger.warn("[net.voice] the Opus native is unavailable, this peer joins without voice."
                    + " Set VoiceConfig.setCodecIdentity(RawPcmVoiceCodec.IDENTITY) on every peer"
                    + " to run a session without it.");
        }
        return opus;
    }
}
