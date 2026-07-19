package fr.epistudio.epysia.editor.gizmo;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class ScreenSpaceWidth {

    private static final float FALLBACK_WORLD_PER_PIXEL = 0.01f;
    private static final float MINIMUM_REFERENCE_DISTANCE = 1.0e-4f;
    private static final float MINIMUM_DEPTH_RATIO = 0.05f;
    private static final float MAXIMUM_DEPTH_RATIO = 50.0f;

    private final Vector3f cameraPosition;
    private final float worldPerPixelAtReference;
    private final float referenceDistance;
    private final float thicknessScale;

    ScreenSpaceWidth(Matrix4f viewProjection, Vector3f cameraPosition, int pixelWidth, float thicknessScale) {
        this.cameraPosition = new Vector3f(cameraPosition);
        this.thicknessScale = thicknessScale;
        Matrix4f inverse = new Matrix4f(viewProjection).invert();
        Vector3f center = unproject(inverse, 0.0f);
        Vector3f right = unproject(inverse, 2.0f / Math.max(1, pixelWidth));
        float distance = center.distance(right);
        this.worldPerPixelAtReference = Float.isFinite(distance) && distance > 1.0e-8f
                ? distance
                : FALLBACK_WORLD_PER_PIXEL;
        this.referenceDistance = Math.max(MINIMUM_REFERENCE_DISTANCE, this.cameraPosition.distance(center));
    }

    float worldHalfWidthAt(Vector3f point, float halfWidthPixels) {
        float ratio = cameraPosition.distance(point) / referenceDistance;
        float clampedRatio = Math.clamp(ratio, MINIMUM_DEPTH_RATIO, MAXIMUM_DEPTH_RATIO);
        return halfWidthPixels * thicknessScale * worldPerPixelAtReference * clampedRatio;
    }

    private static Vector3f unproject(Matrix4f inverse, float ndcX) {
        Vector4f point = new Vector4f(ndcX, 0.0f, 0.0f, 1.0f);
        inverse.transform(point);
        if (Math.abs(point.w) < 1.0e-6f) {
            return new Vector3f();
        }
        return new Vector3f(point.x / point.w, point.y / point.w, point.z / point.w);
    }
}
