package fr.epistudio.epysia.audio;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public final class StreamingAudioSource {

    private static final int BUFFER_COUNT = 4;
    private static final int SAMPLES_PER_BUFFER = 16384;

    private final ByteBuffer encodedAudio;
    private final long decoderHandle;
    private final int channelCount;
    private final int sampleRate;
    private final int openAlFormat;
    private final int[] bufferIds = new int[BUFFER_COUNT];
    private final ShortBuffer scratchPcm = BufferUtils.createShortBuffer(SAMPLES_PER_BUFFER * 2);
    private boolean looping;
    private boolean finished;

    public static StreamingAudioSource fromResource(String relativePath) {
        ByteBuffer encoded = AudioBufferLoader.readResource(relativePath);
        return new StreamingAudioSource(encoded);
    }

    public StreamingAudioSource(ByteBuffer encodedAudio) {
        this.encodedAudio = encodedAudio;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errorBuffer = stack.mallocInt(1);
            this.decoderHandle = STBVorbis.stb_vorbis_open_memory(encodedAudio, errorBuffer, null);
            if (decoderHandle == 0L) {
                throw new EpysiaException("Failed to open OGG stream (stb_vorbis error " + errorBuffer.get(0) + ").");
            }
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(decoderHandle, info);
            this.channelCount = info.channels();
            this.sampleRate = info.sample_rate();
        }
        this.openAlFormat = channelCount == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        AL10.alGenBuffers(bufferIds);
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public boolean primeBuffers(AudioSource source) {
        int queued = 0;
        for (int i = 0; i < BUFFER_COUNT; i++) {
            if (!fillAndQueue(source, bufferIds[i])) {
                break;
            }
            queued++;
        }
        return queued > 0;
    }

    public void pump(AudioSource source) {
        int processed = AL10.alGetSourcei(source.sourceId(), AL10.AL_BUFFERS_PROCESSED);
        while (processed > 0) {
            int bufferId = AL10.alSourceUnqueueBuffers(source.sourceId());
            processed--;
            if (finished) {
                continue;
            }
            if (!fillAndQueue(source, bufferId)) {
                finished = true;
            }
        }
    }

    private boolean fillAndQueue(AudioSource source, int bufferId) {
        int samplesDecoded = decodeIntoScratch();
        if (samplesDecoded <= 0) {
            if (looping) {
                STBVorbis.stb_vorbis_seek_start(decoderHandle);
                samplesDecoded = decodeIntoScratch();
                if (samplesDecoded <= 0) {
                    return false;
                }
            } else {
                return false;
            }
        }
        AL10.alBufferData(bufferId, openAlFormat, scratchPcm, sampleRate);
        AL10.alSourceQueueBuffers(source.sourceId(), bufferId);
        return true;
    }

    private int decodeIntoScratch() {
        scratchPcm.clear();
        int samples = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoderHandle, channelCount, scratchPcm);
        scratchPcm.position(samples * channelCount);
        scratchPcm.flip();
        return samples;
    }

    public boolean finished() {
        return finished;
    }

    public int channelCount() {
        return channelCount;
    }

    public void destroy() {
        STBVorbis.stb_vorbis_close(decoderHandle);
        AL10.alDeleteBuffers(bufferIds);
    }
}
