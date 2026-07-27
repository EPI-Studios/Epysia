package fr.epistudio.epysia.assets.epyinstances;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EpyInstancesWriter {

    private EpyInstancesWriter() {
    }

    public static byte[] write(InstanceTransforms transforms) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(buffer)) {
            stream.writeInt(EpyInstancesFormat.MAGIC);
            stream.writeInt(EpyInstancesFormat.VERSION);
            stream.writeInt(transforms.count());
            for (float value : transforms.models()) {
                stream.writeFloat(value);
            }
        } catch (IOException exception) {
            throw new EpysiaException("Failed to encode .epyinstances: " + exception.getMessage(), exception);
        }
        return buffer.toByteArray();
    }

    public static void writeToFile(Path path, InstanceTransforms transforms) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.write(path, write(transforms));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to write .epyinstances to " + path + ": "
                    + exception.getMessage(), exception);
        }
    }
}
