package fr.epistudio.epysia.audio;

import de.maxhenkel.lame4j.DecodedAudio;
import de.maxhenkel.lame4j.Mp3Decoder;
import de.maxhenkel.lame4j.Mp3Encoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mp3DecodingTest {
    private static final int SAMPLE_RATE = 44_100;
    private static final int CHANNELS = 1;
    private static final int BIT_RATE = 128;
    private static final int QUALITY = 5;
    private static final int TONE_SAMPLES = SAMPLE_RATE;
    private static final float TONE_HERTZ = 440.0f;

    @Test
    void anEncodedToneDecodesBackAtTheSameRate() throws Exception {
        byte[] encoded = encodeTone();
        DecodedAudio decoded = Mp3Decoder.decode(new ByteArrayInputStream(encoded));
        assertEquals(SAMPLE_RATE, decoded.getSampleRate());
        assertEquals(CHANNELS, decoded.getChannelCount());
        assertTrue(decoded.getSamples().length > TONE_SAMPLES / 2,
                "the decode returned only " + decoded.getSamples().length + " samples");
    }

    @Test
    void theDecodedToneStillCarriesEnergy() throws Exception {
        DecodedAudio decoded = Mp3Decoder.decode(new ByteArrayInputStream(encodeTone()));
        double energy = 0.0;
        for (short sample : decoded.getSamples()) {
            energy += (double) sample * sample;
        }
        assertTrue(energy / decoded.getSamples().length > 1_000.0,
                "the decoded audio is silent, which means the round trip lost the signal");
    }

    private static byte[] encodeTone() throws Exception {
        short[] tone = new short[TONE_SAMPLES];
        for (int index = 0; index < tone.length; index++) {
            double phase = 2.0 * Math.PI * TONE_HERTZ * index / SAMPLE_RATE;
            tone[index] = (short) (Math.sin(phase) * Short.MAX_VALUE * 0.5);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Mp3Encoder encoder = new Mp3Encoder(CHANNELS, SAMPLE_RATE, BIT_RATE, QUALITY, output);
        encoder.write(tone);
        encoder.close();
        return output.toByteArray();
    }
}
