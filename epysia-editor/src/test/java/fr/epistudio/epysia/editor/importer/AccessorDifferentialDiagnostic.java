package fr.epistudio.epysia.editor.importer;

import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.io.GltfModelReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class AccessorDifferentialDiagnostic {

    private static final Path SAMPLE = Path.of("/home/meek/dazdazd/Abandoned_House.glb");

    static boolean sampleExists() {
        return Files.isRegularFile(SAMPLE);
    }

    @Test
    @EnabledIf("sampleExists")
    void compareReaders() throws Exception {
        GltfModel model = new GltfModelReader().read(SAMPLE);
        int checked = 0;
        int divergent = 0;
        for (MeshModel mesh : model.getMeshModels()) {
            for (MeshPrimitiveModel primitive : mesh.getMeshPrimitiveModels()) {
                for (Map.Entry<String, AccessorModel> attribute : primitive.getAttributes().entrySet()) {
                    if (attribute.getKey().startsWith("JOINTS")) {
                        continue;
                    }
                    int components = attribute.getValue().getElementType().getNumComponents();
                    float[] ours = GltfImporter.readFloatsForDiagnostic(attribute.getValue(), components);
                    float[] amnetic = amneticRead(attribute.getValue());
                    checked++;
                    String divergence = firstDivergence(ours, amnetic);
                    if (divergence != null) {
                        divergent++;
                        if (divergent <= 8) {
                            System.out.println("DIAG DIVERGENT " + mesh.getName() + " " + attribute.getKey()
                                    + " ct=" + attribute.getValue().getComponentType()
                                    + " norm=" + attribute.getValue().isNormalized() + " -> " + divergence);
                        }
                    }
                }
            }
        }
        System.out.println("DIAG checked=" + checked + " divergent=" + divergent);
    }

    private static String firstDivergence(float[] ours, float[] reference) {
        if (ours.length != reference.length) {
            return "length " + ours.length + " vs " + reference.length;
        }
        for (int index = 0; index < ours.length; index++) {
            if (Math.abs(ours[index] - reference[index]) > 1.0e-6f) {
                return "index " + index + ": " + ours[index] + " vs " + reference[index];
            }
        }
        return null;
    }

    private static float[] amneticRead(AccessorModel accessor) {
        AccessorData data = accessor.getAccessorData();
        ByteBuffer buffer = data.createByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int total = data.getTotalNumComponents();
        float[] out = new float[total];
        Class<?> componentType = data.getComponentType();
        boolean normalized = accessor.isNormalized();
        int gltfComponentType = accessor.getComponentType();
        if (componentType == float.class) {
            for (int i = 0; i < total; i++) {
                out[i] = buffer.getFloat();
            }
            return out;
        }
        if (componentType == byte.class) {
            boolean unsigned = gltfComponentType == 5121;
            for (int i = 0; i < total; i++) {
                int value = unsigned ? Byte.toUnsignedInt(buffer.get()) : buffer.get();
                out[i] = normalized ? (unsigned ? value / 255.0f : Math.max(-1.0f, value / 127.0f)) : value;
            }
            return out;
        }
        if (componentType == short.class) {
            boolean unsigned = gltfComponentType == 5123;
            for (int i = 0; i < total; i++) {
                int value = unsigned ? Short.toUnsignedInt(buffer.getShort()) : buffer.getShort();
                out[i] = normalized ? (unsigned ? value / 65535.0f : Math.max(-1.0f, value / 32767.0f)) : value;
            }
            return out;
        }
        throw new IllegalArgumentException("Unsupported: " + componentType);
    }
}
