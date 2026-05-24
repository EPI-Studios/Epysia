package fr.epistudio.epysia.audio;

public record AudioReverbPreset(
        float density,
        float diffusion,
        float gain,
        float gainHighFrequency,
        float decayTimeSeconds,
        float decayHighFrequencyRatio,
        float reflectionsGain,
        float reflectionsDelaySeconds,
        float lateReverbGain,
        float lateReverbDelaySeconds,
        float airAbsorptionGainHighFrequency,
        float roomRolloffFactor
) {

    public static final AudioReverbPreset GENERIC = new AudioReverbPreset(
            1.0f, 1.0f, 0.32f, 0.89f, 1.49f, 0.83f,
            0.05f, 0.007f, 1.26f, 0.011f, 0.994f, 0.0f);

    public static final AudioReverbPreset CAVE = new AudioReverbPreset(
            1.0f, 1.0f, 0.32f, 1.0f, 2.91f, 1.3f,
            0.5f, 0.015f, 0.706f, 0.022f, 0.994f, 0.0f);

    public static final AudioReverbPreset FOREST = new AudioReverbPreset(
            1.0f, 0.3f, 0.32f, 0.045f, 1.49f, 0.54f,
            0.052f, 0.162f, 0.768f, 0.088f, 0.994f, 0.0f);

    public static final AudioReverbPreset SMALL_ROOM = new AudioReverbPreset(
            1.0f, 1.0f, 0.32f, 0.93f, 0.4f, 0.83f,
            0.15f, 0.003f, 1.0f, 0.004f, 0.994f, 0.0f);
}
