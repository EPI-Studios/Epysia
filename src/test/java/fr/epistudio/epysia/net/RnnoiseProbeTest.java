package fr.epistudio.epysia.net;

import de.maxhenkel.rnnoise4j.Denoiser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RnnoiseProbeTest {
    @Test
    void theFrameSizeDividesOurVoiceFrame() throws Exception {
        try (Denoiser denoiser = new Denoiser()) {
            int frameSize = denoiser.getFrameSize();
            assertTrue(frameSize > 0);
            assertEquals(0, 960 % frameSize,
                    "a 20 ms voice frame must chunk evenly into RNNoise frames, its size is " + frameSize);
        }
    }

    @Test
    void denoisingTakesEnergyOutOfNoise() throws Exception {
        try (Denoiser denoiser = new Denoiser()) {
            short[] noisy = whiteNoise(denoiser.getFrameSize());
            double before = energyOf(noisy);
            for (int pass = 0; pass < 20; pass++) {
                denoiser.denoiseInPlace(noisy);
            }
            assertTrue(energyOf(noisy) < before,
                    "denoising should have reduced noise energy, went from " + before
                            + " to " + energyOf(noisy));
        }
    }

    private static double energyOf(short[] frame) {
        double total = 0.0;
        for (short sample : frame) {
            total += (double) sample * sample;
        }
        return total / frame.length;
    }

    private static short[] whiteNoise(int count) {
        short[] frame = new short[count];
        long state = 99L;
        for (int index = 0; index < count; index++) {
            state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
            frame[index] = (short) ((state >> 48) / 8);
        }
        return frame;
    }
}
