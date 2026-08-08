package fr.epistudio.epysia.net.voice;

final class RawPcmVoiceDecoder implements VoiceDecoder {
    private final short[] lastDecodedFrame = new short[VoiceConfig.FRAME_SAMPLES];

    @Override
    public int decode(byte[] packet, int length, short[] destination) {
        int available = Math.min(length / Short.BYTES, RawPcmVoiceCodec.ENCODED_SAMPLES);
        for (int index = 0; index < available; index++) {
            short sample = (short) ((packet[index * 2] & 0xFF) | (packet[index * 2 + 1] << 8));
            writeUpsampled(destination, index, sample);
        }
        int produced = Math.min(available * RawPcmVoiceCodec.DECIMATION, destination.length);
        System.arraycopy(destination, 0, lastDecodedFrame, 0, produced);
        return produced;
    }

    private static void writeUpsampled(short[] destination, int encodedIndex, short sample) {
        for (int offset = 0; offset < RawPcmVoiceCodec.DECIMATION; offset++) {
            int target = encodedIndex * RawPcmVoiceCodec.DECIMATION + offset;
            if (target < destination.length) {
                destination[target] = sample;
            }
        }
    }

    @Override
    public int conceal(short[] destination) {
        int produced = Math.min(lastDecodedFrame.length, destination.length);
        for (int index = 0; index < produced; index++) {
            destination[index] = (short) (lastDecodedFrame[index] / 2);
        }
        System.arraycopy(destination, 0, lastDecodedFrame, 0, produced);
        return produced;
    }

    @Override
    public void destroy() {
    }
}
