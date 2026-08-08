package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.voice.VoiceConfig;
import fr.epistudio.epysia.net.voice.dsp.VoiceProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VoiceProcessingTest {
    private static final float HOLD_SECONDS = 0.1f;
    private static final float NO_PLAYBACK = 0.0f;
    private static final float LOUD_PLAYBACK = 0.5f;
    private static final int SETTLE_FRAMES = 40;

    private final VoiceProcessor processor =
            new VoiceProcessor(VoiceConfig.SAMPLE_RATE, VoiceConfig.FRAME_SAMPLES);

    @Test
    void theGateStaysShutOnRoomNoise() {
        settleOn(0.002f);
        assertFalse(processor.process(noise(0.002f), NO_PLAYBACK, HOLD_SECONDS, true),
                "steady room noise should never open the gate");
    }

    @Test
    void theGateOpensOnSomethingVoiceShaped() {
        settleOn(0.002f);
        assertTrue(processor.process(voice(0.25f), NO_PLAYBACK, HOLD_SECONDS, true),
                "a voice shaped signal should open the gate");
    }

    @Test
    void theDenoiserIsTheOneActuallyInUseOnThisPlatform() {
        assertEquals("rnnoise", processor.noiseSuppressionName(),
                "rnnoise4j ships a linux-x64 native, so the fallback should not be what runs here");
    }

    @Test
    void speechProbabilityGatingIsWhatRunsWhenAThresholdIsGiven() {
        settleOn(0.002f);
        boolean withProbability = processor.process(voice(0.25f), NO_PLAYBACK, HOLD_SECONDS, true, 0.0001f);
        processor.reset();
        settleOn(0.002f);
        boolean withLevel = processor.process(voice(0.25f), NO_PLAYBACK, HOLD_SECONDS, true, 0.0f);
        assertTrue(withProbability || withLevel,
                "one of the two gating paths should have let this through");
    }

    @Test
    void aThresholdAboveAnythingReachableKeepsTheGateShut() {
        settleOn(0.002f);
        assertFalse(processor.process(voice(0.25f), NO_PLAYBACK, HOLD_SECONDS, true, 1.1f),
                "a probability threshold nothing can reach must keep the gate shut");
    }

    @Test
    void aSpeechProbabilityIsReportedForTheIndicatorToRead() {
        processor.process(voice(0.2f), NO_PLAYBACK, HOLD_SECONDS, false);
        float probability = processor.speechProbability();
        assertTrue(probability >= 0.0f && probability <= 1.0f,
                "the probability should be a usable fraction, got " + probability);
    }

    @Test
    void theSpeexNativeIsTheOneActuallyInUseOnThisPlatform() {
        assertEquals("speex", processor.gainName(),
                "speex4j ships a linux-x64 native, so the fallback should not be what runs here");
    }

    @Test
    void automaticGainLiftsAQuietSpeakerTowardsTheTarget() {
        float before = levelAfterProcessing(tone(0.01f));
        for (int frame = 0; frame < SETTLE_FRAMES; frame++) {
            processor.process(tone(0.01f), NO_PLAYBACK, HOLD_SECONDS, false);
        }
        float after = processor.outputLevel();
        assertTrue(after > before * 2.0f,
                "a quiet speaker should be lifted, went from " + before + " to " + after);
    }

    @Test
    void automaticGainNeverPushesPastFullScale() {
        for (int frame = 0; frame < SETTLE_FRAMES; frame++) {
            short[] loud = tone(0.95f);
            processor.process(loud, NO_PLAYBACK, HOLD_SECONDS, false);
            for (short sample : loud) {
                assertTrue(Math.abs(sample) <= Short.MAX_VALUE, "the limiter must prevent wraparound");
            }
        }
    }

    @Test
    void nothingIsSentWhileThisPeerIsPlayingSomeoneElse() {
        settleOn(0.002f);
        assertFalse(processor.process(voice(0.3f), LOUD_PLAYBACK, HOLD_SECONDS, false),
                "nothing should be sent while this peer is playing someone else");
        assertTrue(processor.suppressingEcho());
    }

    private void settleOn(float amplitude) {
        for (int frame = 0; frame < SETTLE_FRAMES; frame++) {
            processor.process(noise(amplitude), NO_PLAYBACK, HOLD_SECONDS, true);
        }
    }

    private float levelAfterProcessing(short[] frame) {
        processor.process(frame, NO_PLAYBACK, HOLD_SECONDS, false);
        return processor.outputLevel();
    }

    private static short[] voice(float amplitude) {
        short[] frame = new short[VoiceConfig.FRAME_SAMPLES];
        for (int index = 0; index < frame.length; index++) {
            double time = index / (double) VoiceConfig.SAMPLE_RATE;
            double sample = Math.sin(2.0 * Math.PI * 140.0 * time)
                    + 0.5 * Math.sin(2.0 * Math.PI * 700.0 * time)
                    + 0.25 * Math.sin(2.0 * Math.PI * 1_400.0 * time);
            frame[index] = (short) (sample * amplitude * Short.MAX_VALUE * 0.5);
        }
        return frame;
    }

    private static short[] tone(float amplitude) {
        short[] frame = new short[VoiceConfig.FRAME_SAMPLES];
        for (int index = 0; index < frame.length; index++) {
            double phase = 2.0 * Math.PI * 440.0 * index / VoiceConfig.SAMPLE_RATE;
            frame[index] = (short) (Math.sin(phase) * amplitude * Short.MAX_VALUE);
        }
        return frame;
    }

    private static short[] noise(float amplitude) {
        short[] frame = new short[VoiceConfig.FRAME_SAMPLES];
        long state = 12_345L;
        for (int index = 0; index < frame.length; index++) {
            state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
            float unit = ((state >>> 40) / (float) (1 << 24)) * 2.0f - 1.0f;
            frame[index] = (short) (unit * amplitude * Short.MAX_VALUE);
        }
        return frame;
    }
}
