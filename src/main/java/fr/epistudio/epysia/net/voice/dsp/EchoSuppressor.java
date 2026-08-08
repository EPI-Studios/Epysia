package fr.epistudio.epysia.net.voice.dsp;

public final class EchoSuppressor {
    private static final float PLAYBACK_THRESHOLD = 0.01f;
    private static final float SUPPRESSION = 0.12f;
    private static final float HANGOVER_SECONDS = 0.12f;

    private float hangoverSeconds;

    public boolean update(float playbackLevel, float frameSeconds) {
        if (playbackLevel > PLAYBACK_THRESHOLD) {
            hangoverSeconds = HANGOVER_SECONDS;
            return true;
        }
        hangoverSeconds -= frameSeconds;
        return suppressing();
    }

    public void attenuate(short[] frame, int count) {
        if (!suppressing()) {
            return;
        }
        for (int index = 0; index < count; index++) {
            frame[index] = (short) (frame[index] * SUPPRESSION);
        }
    }

    public boolean suppressing() {
        return hangoverSeconds > 0.0f;
    }

    public void reset() {
        hangoverSeconds = 0.0f;
    }
}
