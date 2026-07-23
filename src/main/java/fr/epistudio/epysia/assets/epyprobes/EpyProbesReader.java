package fr.epistudio.epysia.assets.epyprobes;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public final class EpyProbesReader {

    private EpyProbesReader() {
    }

    public static BakedProbes read(byte[] bytes) {
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return readProbes(stream);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to decode .epyprobes: " + exception.getMessage(), exception);
        }
    }

    private static BakedProbes readProbes(DataInputStream stream) throws IOException {
        requireHeader(stream);
        long bakeHash = stream.readLong();
        int resolutionX = stream.readInt();
        int resolutionY = stream.readInt();
        int resolutionZ = stream.readInt();
        Vector3f origin = readVector(stream);
        Vector3f spacing = readVector(stream);
        int probeCount = stream.readInt();
        float[] positions = readFloats(stream);
        float[] coefficients = readFloats(stream);
        requireCount(probeCount, positions, coefficients);
        return new BakedProbes(bakeHash, origin, spacing, resolutionX, resolutionY, resolutionZ,
                positions, coefficients);
    }

    private static void requireHeader(DataInputStream stream) throws IOException {
        int magic = stream.readInt();
        if (magic != EpyProbesFormat.MAGIC) {
            throw new EpysiaException("Not an .epyprobes file, magic was 0x" + Integer.toHexString(magic));
        }
        int version = stream.readInt();
        if (version != EpyProbesFormat.VERSION) {
            throw new EpysiaException("Unsupported .epyprobes version " + version);
        }
        stream.readInt();
    }

    private static void requireCount(int probeCount, float[] positions, float[] coefficients) {
        if (positions.length != probeCount * 3
                || coefficients.length != probeCount * EpyProbesFormat.FLOATS_PER_PROBE) {
            throw new EpysiaException("Probe payload does not match the declared probe count " + probeCount);
        }
    }

    private static Vector3f readVector(DataInputStream stream) throws IOException {
        return new Vector3f(stream.readFloat(), stream.readFloat(), stream.readFloat());
    }

    private static float[] readFloats(DataInputStream stream) throws IOException {
        int length = stream.readInt();
        float[] values = new float[length];
        for (int index = 0; index < length; index++) {
            values[index] = stream.readFloat();
        }
        return values;
    }
}
