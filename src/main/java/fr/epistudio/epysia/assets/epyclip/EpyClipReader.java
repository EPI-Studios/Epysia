package fr.epistudio.epysia.assets.epyclip;

import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.animation.ClipChannel;
import fr.epistudio.epysia.animation.ClipInterpolation;
import fr.epistudio.epysia.animation.ClipProperty;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class EpyClipReader {

    private record Header(String name, float durationSeconds, long skeletonChecksum) {
    }

    private EpyClipReader() {
    }

    public static Clip read(byte[] data) {
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(data))) {
            validateMagicAndVersion(stream);
            Header header = readHeader(stream);
            List<ClipChannel> channels = readChannels(stream);
            return new Clip(header.name(), header.durationSeconds(), header.skeletonChecksum(), channels);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to decode .epyclip: " + exception.getMessage(), exception);
        }
    }

    public static Clip readFile(Path path) {
        try {
            return read(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read .epyclip from " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static void validateMagicAndVersion(DataInputStream stream) throws IOException {
        int magic = stream.readInt();
        if (magic != EpyClipFormat.MAGIC) {
            throw new EpysiaException("Bad .epyclip magic: expected " + EpyClipFormat.MAGIC + " but got " + magic + ".");
        }
        int version = stream.readInt();
        if (version != EpyClipFormat.VERSION) {
            throw new EpysiaException("Unsupported .epyclip version: expected " + EpyClipFormat.VERSION + " but got " + version + ".");
        }
    }

    private static Header readHeader(DataInputStream stream) throws IOException {
        String name = stream.readUTF();
        float durationSeconds = stream.readFloat();
        long skeletonChecksum = stream.readLong();
        return new Header(name, durationSeconds, skeletonChecksum);
    }

    private static List<ClipChannel> readChannels(DataInputStream stream) throws IOException {
        int count = stream.readInt();
        List<ClipChannel> channels = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            channels.add(readChannel(stream));
        }
        return channels;
    }

    private static ClipChannel readChannel(DataInputStream stream) throws IOException {
        int jointIndex = stream.readInt();
        ClipProperty property = readEnum(stream, ClipProperty.values(), "property");
        ClipInterpolation interpolation = readEnum(stream, ClipInterpolation.values(), "interpolation");
        float[] times = readFloats(stream);
        float[] values = readFloats(stream);
        return new ClipChannel(jointIndex, property, interpolation, times, values);
    }

    private static float[] readFloats(DataInputStream stream) throws IOException {
        int length = stream.readInt();
        float[] values = new float[length];
        for (int index = 0; index < length; index++) {
            values[index] = stream.readFloat();
        }
        return values;
    }

    private static <T> T readEnum(DataInputStream stream, T[] values, String label) throws IOException {
        int ordinal = stream.readInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new EpysiaException("Invalid .epyclip " + label + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
