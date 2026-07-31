package fr.epistudio.epysia.render.baking;

import org.joml.Vector3f;

import java.util.List;

final class ImpostorBounds {

    private ImpostorBounds() {
    }

    static Vector3f centerOf(List<ImpostorPart> parts) {
        Vector3f minimum = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f maximum = new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f position = new Vector3f();
        for (ImpostorPart part : parts) {
            float[] positions = part.mesh().positions();
            for (int index = 0; index + 2 < positions.length; index += 3) {
                transform(part, positions, index, position);
                minimum.min(position);
                maximum.max(position);
            }
        }
        return minimum.x > maximum.x ? new Vector3f() : minimum.add(maximum, new Vector3f()).mul(0.5f);
    }

    static float radiusOf(List<ImpostorPart> parts, Vector3f center) {
        float squared = 0.0f;
        Vector3f position = new Vector3f();
        for (ImpostorPart part : parts) {
            float[] positions = part.mesh().positions();
            for (int index = 0; index + 2 < positions.length; index += 3) {
                transform(part, positions, index, position);
                squared = Math.max(squared, position.sub(center).lengthSquared());
            }
        }
        return (float) Math.sqrt(squared);
    }

    private static void transform(ImpostorPart part, float[] positions, int index, Vector3f destination) {
        destination.set(positions[index], positions[index + 1], positions[index + 2]);
        part.transform().transformPosition(destination);
    }
}
