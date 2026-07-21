package fr.epistudio.epysia.editor.importer;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MirroredTransformTest {

    @Test
    void mirroredWorldMatrixSurvivesDecomposition() {
        Matrix4f world = new Matrix4f()
                .translate(2.0f, 1.0f, -3.0f)
                .rotateY((float) Math.toRadians(35.0))
                .scale(-1.0f, 1.0f, 1.0f);
        assertRecomposes(world);
    }

    @Test
    void plainWorldMatrixSurvivesDecomposition() {
        Matrix4f world = new Matrix4f()
                .translate(-4.0f, 0.5f, 8.0f)
                .rotateXYZ(0.3f, 1.1f, -0.4f)
                .scale(2.0f, 0.5f, 3.0f);
        assertRecomposes(world);
    }

    private static void assertRecomposes(Matrix4f world) {
        float[] worldArray = world.get(new float[16]);
        Vector3f translation = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        Vector3f scale = new Vector3f();
        GltfImporter.decomposeWorldTransformForTest(worldArray, translation, rotation, scale);
        Matrix4f recomposed = new Matrix4f().translationRotateScale(translation, rotation, scale);
        Matrix4f difference = new Matrix4f(world).sub(recomposed);
        float largest = 0.0f;
        for (int index = 0; index < 16; index++) {
            largest = Math.max(largest, Math.abs(difference.get(index % 4, index / 4)));
        }
        assertTrue(largest < 1.0e-4f, "recomposition error " + largest);
    }
}
