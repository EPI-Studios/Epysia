package fr.epistudio.epysia.net.voice;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.opus.Opus;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OpusVoiceCodec implements VoiceCodec {
    public static final String IDENTITY = "opus-48000-mono";
    private static final int CHANNELS = 1;
    private static final int DECLARED_PACKET_LOSS_PERCENT = 10;

    private final List<VoiceDecoder> decoders = new ArrayList<>();
    private final ShortBuffer pcmBuffer = BufferUtils.createShortBuffer(VoiceConfig.FRAME_SAMPLES);
    private final ByteBuffer packetBuffer = BufferUtils.createByteBuffer(VoiceConfig.MAXIMUM_PACKET_BYTES);
    private long encoder;

    private OpusVoiceCodec(long encoder) {
        this.encoder = encoder;
    }

    public static Optional<VoiceCodec> tryCreate(int bitrate) {
        try {
            return Optional.of(create(bitrate));
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            return Optional.empty();
        }
    }

    private static OpusVoiceCodec create(int bitrate) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            long encoder = Opus.opus_encoder_create(VoiceConfig.SAMPLE_RATE, CHANNELS,
                    Opus.OPUS_APPLICATION_VOIP, error);
            requireSuccess(error.get(0), "opus_encoder_create");
            configureEncoder(encoder, bitrate);
            return new OpusVoiceCodec(encoder);
        }
    }

    private static void configureEncoder(long encoder, int bitrate) {
        Opus.opus_encoder_ctl(encoder, Opus.OPUS_SET_BITRATE(bitrate));
        Opus.opus_encoder_ctl(encoder, Opus.OPUS_SET_INBAND_FEC(1));
        Opus.opus_encoder_ctl(encoder, Opus.OPUS_SET_PACKET_LOSS_PERC(DECLARED_PACKET_LOSS_PERCENT));
    }

    private static void requireSuccess(int errorCode, String call) {
        if (errorCode != Opus.OPUS_OK) {
            throw new EpysiaException("Opus call " + call + " failed with code " + errorCode);
        }
    }

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
        if (encoder == 0L) {
            return 0;
        }
        pcmBuffer.clear();
        pcmBuffer.put(pcm, 0, Math.min(pcm.length, VoiceConfig.FRAME_SAMPLES)).flip();
        packetBuffer.clear();
        int written = Opus.opus_encode(encoder, pcmBuffer, VoiceConfig.FRAME_SAMPLES, packetBuffer);
        if (written <= 0 || written > destination.length) {
            return 0;
        }
        packetBuffer.limit(written).position(0);
        packetBuffer.get(destination, 0, written);
        return written;
    }

    @Override
    public VoiceDecoder newDecoder() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            long handle = Opus.opus_decoder_create(VoiceConfig.SAMPLE_RATE, CHANNELS, error);
            requireSuccess(error.get(0), "opus_decoder_create");
            VoiceDecoder decoder = new OpusVoiceDecoder(handle);
            decoders.add(decoder);
            return decoder;
        }
    }

    @Override
    public void destroy() {
        for (VoiceDecoder decoder : decoders) {
            decoder.destroy();
        }
        decoders.clear();
        if (encoder != 0L) {
            Opus.opus_encoder_destroy(encoder);
            encoder = 0L;
        }
    }
}
