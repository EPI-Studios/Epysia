package fr.epistudio.epysia.net.voice.dsp;

public final class GainControls {
    private GainControls() {
    }

    public static GainControl create(int frameSamples, int sampleRate) {
        return SpeexGainControl.tryCreate(frameSamples, sampleRate).orElseGet(SimpleGainControl::new);
    }
}
