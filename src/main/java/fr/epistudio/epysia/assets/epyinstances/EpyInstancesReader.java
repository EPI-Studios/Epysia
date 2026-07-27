package fr.epistudio.epysia.assets.epyinstances;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public final class EpyInstancesReader {

    private EpyInstancesReader() {
    }

    public static InstanceTransforms read(byte[] bytes) {
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return readTransforms(stream);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to decode .epyinstances: " + exception.getMessage(), exception);
        }
    }

    private static InstanceTransforms readTransforms(DataInputStream stream) throws IOException {
        int magic = stream.readInt();
        if (magic != EpyInstancesFormat.MAGIC) {
            throw new EpysiaException("Not an .epyinstances file, magic was 0x" + Integer.toHexString(magic));
        }
        int version = stream.readInt();
        if (version != EpyInstancesFormat.VERSION) {
            throw new EpysiaException("Unsupported .epyinstances version " + version);
        }
        int count = stream.readInt();
        float[] models = new float[count * EpyInstancesFormat.FLOATS_PER_INSTANCE];
        for (int index = 0; index < models.length; index++) {
            models[index] = stream.readFloat();
        }
        return new InstanceTransforms(models);
    }
}
