package fr.epistudio.epysia.audio;

import de.maxhenkel.lame4j.DecodedAudio;
import de.maxhenkel.lame4j.Mp3Decoder;
import de.maxhenkel.lame4j.UnknownPlatformException;
import fr.epistudio.epysia.exceptions.EpysiaException;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AudioBufferLoader {
    private static final String RESOURCE_ROOT = "src/main/resources";

    private AudioBufferLoader() {
    }

    public static AudioBuffer loadFromFile(String absolutePath) {
        return decode(absolutePath, readFromFile(Path.of(absolutePath)));
    }

    public static AudioBuffer loadFromResource(String relativePath) {
        return decode(relativePath, readResource(relativePath));
    }

    private static AudioBuffer decode(String relativePath, ByteBuffer raw) {
        if (relativePath.endsWith(".ogg")) {
            return loadOgg(raw);
        }
        if (relativePath.endsWith(".wav")) {
            return loadWav(raw);
        }
        if (relativePath.endsWith(".mp3")) {
            return loadMp3(raw);
        }
        throw new EpysiaException("Unsupported audio format: " + relativePath);
    }

    public static AudioBuffer createSineTone(float frequencyHz, float durationSeconds, int sampleRate, float amplitude) {
        int sampleCount = (int) (durationSeconds * sampleRate);
        ShortBuffer samples = BufferUtils.createShortBuffer(sampleCount);
        double phaseStep = 2.0 * Math.PI * frequencyHz / sampleRate;
        double phase = 0.0;
        for (int i = 0; i < sampleCount; i++) {
            short value = (short) (Math.sin(phase) * amplitude * Short.MAX_VALUE);
            samples.put(value);
            phase += phaseStep;
        }
        samples.flip();
        return AudioBuffer.createFromPcm16(AudioFormat.MONO_16, sampleRate, samples);
    }

    private static AudioBuffer loadOgg(ByteBuffer encoded) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer sampleRate = stack.mallocInt(1);
            ShortBuffer decoded = STBVorbis.stb_vorbis_decode_memory(encoded, channels, sampleRate);
            if (decoded == null) {
                throw new EpysiaException("Failed to decode OGG audio.");
            }
            AudioFormat format = AudioFormat.forChannelCount(channels.get(0));
            return AudioBuffer.createFromPcm16(format, sampleRate.get(0), decoded);
        }
    }

    private static AudioBuffer loadMp3(ByteBuffer encoded) {
        byte[] bytes = new byte[encoded.remaining()];
        encoded.duplicate().get(bytes);
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            DecodedAudio decoded = Mp3Decoder.decode(stream);
            return AudioBuffer.createFromPcm16(AudioFormat.forChannelCount(decoded.getChannelCount()),
                    decoded.getSampleRate(), toShortBuffer(decoded.getSamples()));
        } catch (IOException | UnknownPlatformException | RuntimeException failure) {
            throw new EpysiaException("Failed to decode MP3 audio.", failure);
        }
    }

    private static ShortBuffer toShortBuffer(short[] samples) {
        ShortBuffer buffer = BufferUtils.createShortBuffer(samples.length);
        buffer.put(samples).flip();
        return buffer;
    }

    private static AudioBuffer loadWav(ByteBuffer data) {
        data.order(ByteOrder.LITTLE_ENDIAN);
        if (!readFourCC(data).equals("RIFF")) {
            throw new EpysiaException("Not a WAV file: missing RIFF header.");
        }
        data.getInt();
        if (!readFourCC(data).equals("WAVE")) {
            throw new EpysiaException("Not a WAV file: missing WAVE marker.");
        }
        WaveFormatChunk fmt = null;
        ShortBuffer samples = null;
        while (data.remaining() >= 8) {
            String chunkId = readFourCC(data);
            int chunkSize = data.getInt();
            int chunkEnd = data.position() + chunkSize;
            switch (chunkId) {
                case "fmt " -> fmt = readFormatChunk(data, chunkSize);
                case "data" -> samples = readDataChunk(data, chunkSize);
                default -> {
                }
            }
            data.position(Math.min(chunkEnd, data.limit()));
        }
        if (fmt == null || samples == null) {
            throw new EpysiaException("WAV file missing fmt or data chunk.");
        }
        return AudioBuffer.createFromPcm16(AudioFormat.forChannelCount(fmt.channelCount()), fmt.sampleRate(), samples);
    }

    private static WaveFormatChunk readFormatChunk(ByteBuffer data, int chunkSize) {
        int formatTag = data.getShort() & 0xFFFF;
        int channels = data.getShort() & 0xFFFF;
        int sampleRate = data.getInt();
        data.getInt();
        data.getShort();
        int bitsPerSample = data.getShort() & 0xFFFF;
        if (formatTag != 1 || bitsPerSample != 16) {
            throw new EpysiaException("Only 16-bit PCM WAV is supported (format=" + formatTag + ", bits=" + bitsPerSample + ").");
        }
        return new WaveFormatChunk(channels, sampleRate);
    }

    private static ShortBuffer readDataChunk(ByteBuffer data, int chunkSize) {
        int byteCount = Math.min(chunkSize, data.remaining());
        ByteBuffer pcm = BufferUtils.createByteBuffer(byteCount);
        int originalLimit = data.limit();
        data.limit(data.position() + byteCount);
        pcm.put(data);
        data.limit(originalLimit);
        pcm.flip();
        pcm.order(ByteOrder.LITTLE_ENDIAN);
        return pcm.asShortBuffer();
    }

    private static String readFourCC(ByteBuffer data) {
        byte[] bytes = new byte[4];
        data.get(bytes);
        return new String(bytes);
    }

    static ByteBuffer readResource(String relativePath) {
        Path absolute = Path.of(RESOURCE_ROOT).resolve(relativePath);
        if (Files.isRegularFile(absolute)) {
            return readFromFile(absolute);
        }
        return readFromClasspath(relativePath);
    }

    private static ByteBuffer readFromFile(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read audio file " + path + ": " + exception.getMessage());
        }
    }

    private static ByteBuffer readFromClasspath(String relativePath) {
        try (InputStream stream = AudioBufferLoader.class.getClassLoader().getResourceAsStream(relativePath)) {
            if (stream == null) {
                throw new EpysiaException("Audio resource not found: " + relativePath);
            }
            byte[] bytes = stream.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read audio resource " + relativePath + ": " + exception.getMessage());
        }
    }

    private record WaveFormatChunk(int channelCount, int sampleRate) {
    }
}
