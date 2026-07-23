package fr.epistudio.epysia.assets.epyprobes;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Vector3f;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EpyProbesWriter {

    private EpyProbesWriter() {
    }

    public static byte[] write(BakedProbes probes) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(buffer)) {
            writeHeader(stream, probes);
            writeFloats(stream, probes.positions());
            writeFloats(stream, probes.coefficients());
        } catch (IOException exception) {
            throw new EpysiaException("Failed to encode .epyprobes: " + exception.getMessage(), exception);
        }
        return buffer.toByteArray();
    }

    public static void writeToFile(Path path, BakedProbes probes) {
        try {
            Files.write(path, write(probes));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to write .epyprobes to " + path + ": "
                    + exception.getMessage(), exception);
        }
    }

    private static void writeHeader(DataOutputStream stream, BakedProbes probes) throws IOException {
        stream.writeInt(EpyProbesFormat.MAGIC);
        stream.writeInt(EpyProbesFormat.VERSION);
        stream.writeInt(0);
        stream.writeLong(probes.bakeHash());
        stream.writeInt(probes.resolutionX());
        stream.writeInt(probes.resolutionY());
        stream.writeInt(probes.resolutionZ());
        writeVector(stream, probes.gridOrigin(new Vector3f()));
        writeVector(stream, probes.gridSpacing(new Vector3f()));
        stream.writeInt(probes.probeCount());
    }

    private static void writeVector(DataOutputStream stream, Vector3f vector) throws IOException {
        stream.writeFloat(vector.x);
        stream.writeFloat(vector.y);
        stream.writeFloat(vector.z);
    }

    private static void writeFloats(DataOutputStream stream, float[] values) throws IOException {
        stream.writeInt(values.length);
        for (float value : values) {
            stream.writeFloat(value);
        }
    }
}
