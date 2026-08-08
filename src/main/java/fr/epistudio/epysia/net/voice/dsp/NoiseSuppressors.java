package fr.epistudio.epysia.net.voice.dsp;

public final class NoiseSuppressors {
    private NoiseSuppressors() {
    }

    public static NoiseSuppression create() {
        return RnnoiseSuppression.tryCreate().orElseGet(NoNoiseSuppression::new);
    }
}
