package fr.epistudio.epysia.physics;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public final class PlatformCarry {
    private PlatformCarry() {
    }

    public static Vector2f delta(Matrix3x2f previousPose, Matrix3x2f currentPose,
                                 float worldX, float worldY, Matrix3x2f scratch, Vector2f out) {
        if (previousPose.equals(currentPose)) {
            return out.set(0.0f, 0.0f);
        }
        out.set(worldX, worldY);
        previousPose.invert(scratch).transformPosition(out);
        currentPose.transformPosition(out);
        return out.sub(worldX, worldY);
    }
}
