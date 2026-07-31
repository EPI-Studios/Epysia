package fr.epistudio.epysia.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObliqueFrustumPreservationTest {

    private static final float TOLERANCE = 1.0e-3f;

    private static Matrix4f perspective() {
        return new Matrix4f().perspective((float) Math.toRadians(60.0), 16.0f / 9.0f, 0.1f, 100.0f);
    }

    private static int visibleBeyondPlane(Vector4f plane) {
        Matrix4f oblique = ObliqueProjection.withObliqueNearPlane(perspective(), plane, new Matrix4f());
        int visible = 0;
        for (float z = -1.0f; z > -60.0f; z -= 0.5f) {
            for (float x = -20.0f; x <= 20.0f; x += 1.0f) {
                for (float y = -6.0f; y <= 6.0f; y += 1.5f) {
                    if (plane.x * x + plane.y * y + plane.z * z + plane.w > 0.0f) {
                        continue;
                    }
                    if (insideClipVolume(oblique, x, y, z)) {
                        visible++;
                    }
                }
            }
        }
        return visible;
    }

    private static boolean insideClipVolume(Matrix4f matrix, float x, float y, float z) {
        Vector4f clip = new Vector4f(x, y, z, 1.0f).mul(matrix);
        if (clip.w <= 0.0f) {
            return false;
        }
        return Math.abs(clip.z / clip.w) <= 1.0f
                && Math.abs(clip.x / clip.w) <= 1.0f
                && Math.abs(clip.y / clip.w) <= 1.0f;
    }

    @Test
    void aTiltedClipPlaneKeepsAlmostAsMuchGeometryAsAHeadOnOne() {
        int headOn = visibleBeyondPlane(new Vector4f(0.0f, 0.0f, 1.0f, 5.0f));
        int tilted = visibleBeyondPlane(new Vector4f(0.5f, 0.0f, 0.866f, 5.0f));

        assertTrue(headOn > 10000, "the head-on case must keep a large part of the frustum");
        assertTrue(tilted > headOn / 2,
                "a tilted portal kept " + tilted + " against " + headOn
                        + " head-on, which is the frustum collapse caused by the wrong corner sign");
    }

    @Test
    void steeplyTiltedPlanesStillShowGeometry() {
        assertTrue(visibleBeyondPlane(new Vector4f(0.707f, 0.0f, 0.707f, 8.0f)) > 10000);
        assertTrue(visibleBeyondPlane(new Vector4f(-0.707f, 0.2f, 0.68f, 8.0f)) > 10000);
        assertTrue(visibleBeyondPlane(new Vector4f(0.94f, 0.0f, 0.34f, 12.0f)) > 5000);
    }

    @Test
    void theNearPlaneStillLandsOnTheClipPlane() {
        Matrix4f oblique = ObliqueProjection.withObliqueNearPlane(perspective(),
                new Vector4f(0.5f, 0.0f, 0.866f, 5.0f), new Matrix4f());

        Vector4f onPlane = new Vector4f(0.0f, 0.0f, -5.0f / 0.866f, 1.0f).mul(oblique);

        assertEquals(-1.0f, onPlane.z / onPlane.w, TOLERANCE);
    }
}
