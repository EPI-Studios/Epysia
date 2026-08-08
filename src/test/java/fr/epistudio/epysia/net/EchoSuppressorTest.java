package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.voice.dsp.EchoSuppressor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EchoSuppressorTest {
    private static final float FRAME_SECONDS = 0.02f;
    private static final float LOUD_PLAYBACK = 0.5f;
    private static final float SILENT_PLAYBACK = 0.0f;
    private static final short SAMPLE = 10_000;

    private final EchoSuppressor suppressor = new EchoSuppressor();

    @Test
    void aFrameIsQuietedWhileTheFarEndIsPlaying() {
        suppressor.update(LOUD_PLAYBACK, FRAME_SECONDS);
        short[] frame = frameOf(SAMPLE);
        suppressor.attenuate(frame, frame.length);
        assertTrue(Math.abs(frame[0]) < SAMPLE / 2,
                "the frame should have been quieted but sits at " + frame[0]);
    }

    @Test
    void aFrameIsUntouchedWhenNothingIsPlaying() {
        suppressor.update(SILENT_PLAYBACK, FRAME_SECONDS);
        short[] frame = frameOf(SAMPLE);
        suppressor.attenuate(frame, frame.length);
        assertEquals(SAMPLE, frame[0]);
    }

    @Test
    void suppressionOutlastsTheFarEndByItsHangover() {
        suppressor.update(LOUD_PLAYBACK, FRAME_SECONDS);
        assertTrue(suppressor.update(SILENT_PLAYBACK, FRAME_SECONDS),
                "a single quiet frame should not immediately reopen the microphone");
        for (int frame = 0; frame < 20; frame++) {
            suppressor.update(SILENT_PLAYBACK, FRAME_SECONDS);
        }
        assertFalse(suppressor.suppressing(), "the hangover should eventually run out");
    }

    private static short[] frameOf(short value) {
        short[] frame = new short[960];
        java.util.Arrays.fill(frame, value);
        return frame;
    }
}
