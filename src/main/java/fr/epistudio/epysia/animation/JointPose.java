package fr.epistudio.epysia.animation;

import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class JointPose {

    private final Vector3f translation = new Vector3f(0.0f, 0.0f, 0.0f);
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f scale = new Vector3f(1.0f, 1.0f, 1.0f);

    public Vector3f translation() {
        return translation;
    }

    public Quaternionf rotation() {
        return rotation;
    }

    public Vector3f scale() {
        return scale;
    }

    public void setFromMatrix(Matrix4fc matrix) {
        matrix.getTranslation(translation);
        matrix.getUnnormalizedRotation(rotation);
        rotation.normalize();
        matrix.getScale(scale);
    }

    public void blendFrom(JointPose from, float alpha) {
        from.translation.lerp(translation, alpha, translation);
        from.scale.lerp(scale, alpha, scale);
        from.rotation.nlerp(rotation, alpha, rotation);
    }

    public void blendToward(JointPose target, float weight) {
        translation.lerp(target.translation, weight);
        scale.lerp(target.scale, weight);
        rotation.nlerp(target.rotation, weight);
    }
}
