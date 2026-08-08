package fr.epistudio.epysia.net.voice;

import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.opus.Opus;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

final class OpusVoiceDecoder implements VoiceDecoder {
    private final ShortBuffer pcmBuffer = BufferUtils.createShortBuffer(VoiceConfig.FRAME_SAMPLES);
    private final ByteBuffer packetBuffer = BufferUtils.createByteBuffer(VoiceConfig.MAXIMUM_PACKET_BYTES);
    private long decoder;

    OpusVoiceDecoder(long decoder) {
        this.decoder = decoder;
    }

    @Override
    public int decode(byte[] packet, int length, short[] destination) {
        if (decoder == 0L || length <= 0 || length > packetBuffer.capacity()) {
            return 0;
        }
        packetBuffer.clear();
        packetBuffer.put(packet, 0, length).flip();
        pcmBuffer.clear();
        return copyDecoded(Opus.opus_decode(decoder, packetBuffer, pcmBuffer, VoiceConfig.FRAME_SAMPLES, 0),
                destination);
    }

    @Override
    public int conceal(short[] destination) {
        if (decoder == 0L) {
            return 0;
        }
        pcmBuffer.clear();
        int samples = Opus.nopus_decode(decoder, MemoryUtil.NULL, 0,
                MemoryUtil.memAddress(pcmBuffer), VoiceConfig.FRAME_SAMPLES, 0);
        return copyDecoded(samples, destination);
    }

    private int copyDecoded(int samples, short[] destination) {
        if (samples <= 0) {
            return 0;
        }
        int copied = Math.min(samples, destination.length);
        pcmBuffer.limit(copied).position(0);
        pcmBuffer.get(destination, 0, copied);
        return copied;
    }

    @Override
    public void destroy() {
        if (decoder == 0L) {
            return;
        }
        Opus.opus_decoder_destroy(decoder);
        decoder = 0L;
    }
}
