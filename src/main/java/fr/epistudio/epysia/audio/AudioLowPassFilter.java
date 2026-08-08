package fr.epistudio.epysia.audio;

import org.lwjgl.openal.EXTEfx;

public final class AudioLowPassFilter {
    private final int filterId;

    public AudioLowPassFilter() {
        filterId = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(filterId, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
    }

    public AudioLowPassFilter setGains(float gain, float highFrequencyGain) {
        EXTEfx.alFilterf(filterId, EXTEfx.AL_LOWPASS_GAIN, Math.clamp(gain, 0.0f, 1.0f));
        EXTEfx.alFilterf(filterId, EXTEfx.AL_LOWPASS_GAINHF, Math.clamp(highFrequencyGain, 0.0f, 1.0f));
        return this;
    }

    public int filterId() {
        return filterId;
    }

    public void destroy() {
        EXTEfx.alDeleteFilters(filterId);
    }
}
