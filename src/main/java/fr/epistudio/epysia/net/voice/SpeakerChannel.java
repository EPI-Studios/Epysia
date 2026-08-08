package fr.epistudio.epysia.net.voice;

import java.util.Optional;

final class SpeakerChannel {
    private final VoiceJitterBuffer jitterBuffer;
    private final VoiceDecoder decoder;
    private final VoicePlayback playback;
    private boolean speaking;

    SpeakerChannel(VoiceJitterBuffer jitterBuffer, VoiceDecoder decoder, boolean audioAvailable) {
        this.jitterBuffer = jitterBuffer;
        this.decoder = decoder;
        this.playback = audioAvailable ? new VoicePlayback() : null;
    }

    VoiceJitterBuffer jitterBuffer() {
        return jitterBuffer;
    }

    VoiceDecoder decoder() {
        return decoder;
    }

    Optional<VoicePlayback> playback() {
        return Optional.ofNullable(playback);
    }

    boolean isSpeaking() {
        return speaking;
    }

    void setSpeaking(boolean value) {
        this.speaking = value;
    }

    void destroy() {
        jitterBuffer.clear();
        decoder.destroy();
        if (playback != null) {
            playback.destroy();
        }
    }
}
