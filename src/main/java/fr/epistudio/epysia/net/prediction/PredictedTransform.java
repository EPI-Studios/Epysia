package fr.epistudio.epysia.net.prediction;

import fr.epistudio.epysia.components.transforms.Transform3D;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record PredictedTransform(Vector3f position, Quaternionf rotation) {
    public static PredictedTransform capturedFrom(Transform3D transform) {
        return new PredictedTransform(new Vector3f(transform.position()), new Quaternionf(transform.rotation()));
    }

    public void applyTo(Transform3D transform) {
        transform.setPosition(position.x, position.y, position.z);
        transform.setRotation(rotation);
    }

    public float distanceTo(PredictedTransform other) {
        return position.distance(other.position);
    }
}
