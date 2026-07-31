package fr.epistudio.epysia.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObliqueProjectionTest {

    private static final float TOLERANCE = 1.0e-3f;

    private static Matrix4f perspective() {
        return new Matrix4f().perspective((float) Math.toRadians(60.0), 16.0f / 9.0f, 0.1f, 100.0f);
    }

    private static float normalizedDepthOf(Matrix4f projection, float x, float y, float z) {
        Vector4f clip = new Vector4f(x, y, z, 1.0f).mul(projection);
        return clip.z / clip.w;
    }

    @Test
    void pointsOnTheClipPlaneLandOnTheNearPlane() {
        Vector4f plane = new Vector4f(0.0f, 0.0f, 1.0f, 5.0f);
        Matrix4f oblique = ObliqueProjection.withObliqueNearPlane(perspective(), plane, new Matrix4f());

        assertEquals(-1.0f, normalizedDepthOf(oblique, 0.0f, 0.0f, -5.0f), TOLERANCE);
        assertEquals(-1.0f, normalizedDepthOf(oblique, 1.5f, -0.8f, -5.0f), TOLERANCE);
    }

    @Test
    void geometryInFrontOfTheClipPlaneFallsOutsideTheNearPlane() {
        Vector4f plane = new Vector4f(0.0f, 0.0f, 1.0f, 5.0f);
        Matrix4f oblique = ObliqueProjection.withObliqueNearPlane(perspective(), plane, new Matrix4f());

        assertTrue(normalizedDepthOf(oblique, 0.0f, 0.0f, -3.0f) < -1.0f,
                "a point between the camera and the portal plane must be clipped away");
        assertTrue(normalizedDepthOf(oblique, 0.0f, 0.0f, -8.0f) > -1.0f,
                "a point beyond the portal plane must stay visible");
    }

    @Test
    void aTiltedPlaneStillMapsOntoTheNearPlane() {
        Vector3f normal = new Vector3f(0.3f, 0.2f, 1.0f).normalize();
        Vector3f pointOnPlane = new Vector3f(0.0f, 0.0f, -6.0f);
        float offset = -normal.dot(pointOnPlane);
        Vector4f plane = new Vector4f(normal.x, normal.y, normal.z, offset);

        Matrix4f oblique = ObliqueProjection.withObliqueNearPlane(perspective(), plane, new Matrix4f());

        assertEquals(-1.0f, normalizedDepthOf(oblique, pointOnPlane.x, pointOnPlane.y, pointOnPlane.z),
                TOLERANCE);
    }

    @Test
    void aDegeneratePlaneLeavesTheProjectionUntouched() {
        Matrix4f original = perspective();

        Matrix4f result = ObliqueProjection.withObliqueNearPlane(original,
                new Vector4f(0.0f, 0.0f, 0.0f, 1.0f), new Matrix4f());

        assertEquals(original, result);
    }

    @Test
    void planeFromBuildsAViewSpacePlaneThroughTheGivenPoint() {
        Matrix4f view = new Matrix4f().lookAt(new Vector3f(0.0f, 0.0f, 4.0f),
                new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f));

        Vector4f plane = ObliqueProjection.planeFrom(new Vector3f(0.0f, 0.0f, 0.0f),
                new Vector3f(0.0f, 0.0f, 1.0f), view);

        Vector4f viewSpacePoint = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f).mul(view);
        assertEquals(0.0f, plane.dot(viewSpacePoint), TOLERANCE);
    }
}
