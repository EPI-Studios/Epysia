package fr.epistudio.epysia.net.voice;

public final class RawPcmVoiceCodec implements VoiceCodec {
    public static final String IDENTITY = "pcm16-16000-mono";
    static final int DECIMATION = 3;
    static final int ENCODED_SAMPLES = VoiceConfig.FRAME_SAMPLES / DECIMATION;

    @Override
    public String identity() {
        return IDENTITY;
    }

    @Override
    public int sampleRate() {
        return VoiceConfig.SAMPLE_RATE;
    }

    @Override
    public int frameSamples() {
        return VoiceConfig.FRAME_SAMPLES;
    }

    @Override
    public int encode(short[] pcm, byte[] destination) {
        int required = ENCODED_SAMPLES * Short.BYTES;
        if (destination.length < required) {
            return 0;
        }
        for (int index = 0; index < ENCODED_SAMPLES; index++) {
            short averaged = averageOf(pcm, index * DECIMATION);
            destination[index * 2] = (byte) (averaged & 0xFF);
            destination[index * 2 + 1] = (byte) ((averaged >> 8) & 0xFF);
        }
        return required;
    }

    private static short averageOf(short[] pcm, int start) {
        int total = 0;
        int counted = 0;
        for (int offset = 0; offset < DECIMATION && start + offset < pcm.length; offset++) {
            total += pcm[start + offset];
            counted++;
        }
        return counted == 0 ? 0 : (short) (total / counted);
    }

    @Override
    public VoiceDecoder newDecoder() {
        return new RawPcmVoiceDecoder();
    }

    @Override
    public void destroy() {
    }
}
