package fr.epistudio.epysia.editor.inspector;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class EulerCache {

    private static final float RADIANS_TO_DEGREES = 57.295776f;
    private static final float DEGREES_TO_RADIANS = 0.017453293f;
    private static final float QUAT_EPSILON = 0.0001f;

    private final Vector3f degrees = new Vector3f();
    private final Quaternionf lastSeenQuaternion = new Quaternionf();
    private boolean initialized;

    public Vector3f degrees() {
        return degrees;
    }

    public void refreshFromIfChanged(Quaternionf currentQuaternion, boolean anyAxisEditing) {
        if (anyAxisEditing) {
            return;
        }
        if (initialized && !quaternionsDiffer(lastSeenQuaternion, currentQuaternion)) {
            return;
        }
        currentQuaternion.getEulerAnglesYXZ(degrees);
        degrees.mul(RADIANS_TO_DEGREES);
        lastSeenQuaternion.set(currentQuaternion);
        initialized = true;
    }

    public void applyEulerToQuaternion(float pitchDegrees, float yawDegrees, float rollDegrees,
                                       Quaternionf destination) {
        degrees.set(pitchDegrees, yawDegrees, rollDegrees);
        destination.identity()
                .rotateY(yawDegrees * DEGREES_TO_RADIANS)
                .rotateX(pitchDegrees * DEGREES_TO_RADIANS)
                .rotateZ(rollDegrees * DEGREES_TO_RADIANS);
        lastSeenQuaternion.set(destination);
    }

    private static boolean quaternionsDiffer(Quaternionf a, Quaternionf b) {
        float dot = Math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w);
        return dot < (1.0f - QUAT_EPSILON);
    }
}
