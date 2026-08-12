package fr.epistudio.epysia.navigation;

import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class NavGeometry {

    private final List<Float> vertices = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();
    private final Vector3f scratch = new Vector3f();

    public void addTriangles(float[] positions, int[] triangleIndices, Matrix4fc worldTransform) {
        int firstVertex = vertices.size() / 3;
        for (int offset = 0; offset + 2 < positions.length; offset += 3) {
            scratch.set(positions[offset], positions[offset + 1], positions[offset + 2]);
            worldTransform.transformPosition(scratch);
            vertices.add(scratch.x);
            vertices.add(scratch.y);
            vertices.add(scratch.z);
        }
        for (int index : triangleIndices) {
            indices.add(firstVertex + index);
        }
    }

    public boolean isEmpty() {
        return indices.isEmpty();
    }

    public int triangleCount() {
        return indices.size() / 3;
    }

    public float[] vertexArray() {
        float[] packed = new float[vertices.size()];
        for (int index = 0; index < packed.length; index++) {
            packed[index] = vertices.get(index);
        }
        return packed;
    }

    public int[] indexArray() {
        int[] packed = new int[indices.size()];
        for (int index = 0; index < packed.length; index++) {
            packed[index] = indices.get(index);
        }
        return packed;
    }
}
