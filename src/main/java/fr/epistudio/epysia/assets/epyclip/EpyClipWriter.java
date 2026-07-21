package fr.epistudio.epysia.assets.epyclip;

import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.animation.ClipChannel;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EpyClipWriter {

    private EpyClipWriter() {
    }

    public static byte[] write(Clip clip) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(buffer)) {
            writeHeader(stream, clip);
            writeChannels(stream, clip);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to encode .epyclip: " + exception.getMessage(), exception);
        }
        return buffer.toByteArray();
    }

    public static void writeToFile(Path path, Clip clip) {
        try {
            Files.write(path, write(clip));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to write .epyclip to " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static void writeHeader(DataOutputStream stream, Clip clip) throws IOException {
        stream.writeInt(EpyClipFormat.MAGIC);
        stream.writeInt(EpyClipFormat.VERSION);
        stream.writeUTF(clip.name());
        stream.writeFloat(clip.durationSeconds());
        stream.writeLong(clip.skeletonChecksum());
    }

    private static void writeChannels(DataOutputStream stream, Clip clip) throws IOException {
        stream.writeInt(clip.channels().size());
        for (ClipChannel channel : clip.channels()) {
            writeChannel(stream, channel);
        }
    }

    private static void writeChannel(DataOutputStream stream, ClipChannel channel) throws IOException {
        stream.writeInt(channel.jointIndex());
        stream.writeInt(channel.property().ordinal());
        stream.writeInt(channel.interpolation().ordinal());
        writeFloats(stream, channel.times());
        writeFloats(stream, channel.values());
    }

    private static void writeFloats(DataOutputStream stream, float[] values) throws IOException {
        stream.writeInt(values.length);
        for (float value : values) {
            stream.writeFloat(value);
        }
    }
}
