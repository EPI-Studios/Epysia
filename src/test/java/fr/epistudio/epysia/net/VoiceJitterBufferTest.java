package fr.epistudio.epysia.net;

import fr.epistudio.epysia.net.voice.VoiceJitterBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class VoiceJitterBufferTest {
    private static final int TARGET_FRAMES = 3;

    @Test
    void framesComeOutInSequenceOrderWhateverOrderTheyArriveIn() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(TARGET_FRAMES);
        buffer.push(2, payload(2));
        buffer.push(0, payload(0));
        buffer.push(1, payload(1));
        assertArrayEquals(payload(0), buffer.pop().payload());
        assertArrayEquals(payload(1), buffer.pop().payload());
        assertArrayEquals(payload(2), buffer.pop().payload());
    }

    @Test
    void aFrameLaterThanItsSlotIsDroppedRatherThanPlayed() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(1);
        buffer.push(5, payload(5));
        buffer.pop();
        assertFalse(buffer.push(4, payload(4)));
        assertEquals(1L, buffer.lateFrames());
    }

    @Test
    void aMissingSequenceNumberYieldsAConcealedFrame() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(TARGET_FRAMES);
        buffer.push(0, payload(0));
        buffer.push(2, payload(2));
        buffer.push(3, payload(3));
        assertEquals(VoiceJitterBuffer.Kind.PLAY, buffer.pop().kind());
        assertEquals(VoiceJitterBuffer.Kind.CONCEAL, buffer.pop().kind());
        assertArrayEquals(payload(2), buffer.pop().payload());
    }

    @Test
    void anEmptyBufferReportsSilenceRatherThanConcealing() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(TARGET_FRAMES);
        assertEquals(VoiceJitterBuffer.Kind.SILENCE, buffer.pop().kind());
    }

    private static byte[] payload(int marker) {
        return new byte[]{(byte) marker, (byte) (marker + 100)};
    }
}
