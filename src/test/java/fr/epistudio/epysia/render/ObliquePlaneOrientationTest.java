package fr.epistudio.epysia.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObliquePlaneOrientationTest {

    private static final float TOLERANCE = 1.0e-3f;

    private static Matrix4f perspective() {
        return new Matrix4f().perspective((float) Math.toRadians(60.0), 16.0f / 9.0f, 0.1f, 100.0f);
    }

    private static float normalizedDepthOf(Matrix4f projection, float z) {
        Vector4f clip = new Vector4f(0.0f, 0.0f, z, 1.0f).mul(projection);
        return clip.z / clip.w;
    }

    @Test
    void aPlaneWhoseNormalPointsAwayFromTheCameraIsFlipped() {
        Vector4f awayFromCamera = new Vector4f(0.0f, 0.0f, -1.0f, -5.0f);

        Vector4f oriented = ObliqueProjection.facingCamera(new Vector4f(awayFromCamera));

        assertTrue(oriented.w > 0.0f, "the camera must end up on the clipped side");
        assertEquals(1.0f, oriented.z, TOLERANCE);
    }

    @Test
    void bothPlaneOrientationsProduceTheSameClipping() {
        Matrix4f fromFacing = ObliqueProjection.withObliqueNearPlane(perspective(),
                ObliqueProjection.facingCamera(new Vector4f(0.0f, 0.0f, 1.0f, 5.0f)), new Matrix4f());
        Matrix4f fromReversed = ObliqueProjection.withObliqueNearPlane(perspective(),
                ObliqueProjection.facingCamera(new Vector4f(0.0f, 0.0f, -1.0f, -5.0f)), new Matrix4f());

        assertEquals(-1.0f, normalizedDepthOf(fromFacing, -5.0f), TOLERANCE);
        assertEquals(-1.0f, normalizedDepthOf(fromReversed, -5.0f), TOLERANCE);
        assertTrue(fromFacing.equals(fromReversed, 1.0e-4f),
                "a portal seen from either side must clip identically");
    }

    @Test
    void planeFromOrientsTowardsTheCameraWhicheverWayTheSurfaceFaces() {
        Matrix4f view = new Matrix4f().lookAt(new Vector3f(0.0f, 0.0f, 4.0f),
                new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f));

        Vector4f towards = ObliqueProjection.planeFrom(new Vector3f(0.0f, 0.0f, 0.0f),
                new Vector3f(0.0f, 0.0f, 1.0f), view);
        Vector4f away = ObliqueProjection.planeFrom(new Vector3f(0.0f, 0.0f, 0.0f),
                new Vector3f(0.0f, 0.0f, -1.0f), view);

        assertTrue(towards.w > 0.0f);
        assertTrue(away.w > 0.0f);
    }
}
