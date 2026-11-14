package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;

public final class UiWorldPlacement {

    private UiWorldPlacement() {
    }

    public static Matrix4f canvasToWorld(UiCanvas canvas, Transform3D transform, Camera3D camera,
                                         float interpolationAlpha) {
        Matrix4f objectWorld = new Matrix4f(transform.worldMatrix(interpolationAlpha));
        Matrix4f placement = canvas.space() == UiCanvasSpace.BILLBOARD
                ? facingCamera(objectWorld, camera, interpolationAlpha)
                : objectWorld;
        float metresPerPixel = 1.0f / canvas.pixelsPerMetre();
        Vector2f size = canvas.worldSize();
        return placement.scale(metresPerPixel, -metresPerPixel, 1.0f)
                .translate(-size.x() * 0.5f, -size.y() * 0.5f, 0.0f);
    }

    public static boolean beyondMaxDistance(UiCanvas canvas, Transform3D transform, Camera3D camera,
                                            float interpolationAlpha) {
        if (canvas.maxDistance() <= 0.0f) {
            return false;
        }
        Vector3f cameraPosition = camera.position(new Vector3f(), interpolationAlpha);
        Vector3f canvasPosition = transform.worldMatrix(interpolationAlpha).getTranslation(new Vector3f());
        return cameraPosition.distance(canvasPosition) > canvas.maxDistance();
    }

    private static Matrix4f facingCamera(Matrix4f objectWorld, Camera3D camera, float interpolationAlpha) {
        Vector3f position = objectWorld.getTranslation(new Vector3f());
        Matrix4f cameraWorld = new Matrix4f(camera.view(interpolationAlpha)).invert();
        return new Matrix4f().set3x3(cameraWorld).setTranslation(position);
    }

    public static Optional<Vector2f> canvasPointUnderCursor(Matrix4f canvasToWorld, Matrix4f viewProjection,
                                                            float normalizedX, float normalizedY) {
        Matrix4f clipToCanvas = new Matrix4f(viewProjection).mul(canvasToWorld).invert();
        Vector3f near = unproject(clipToCanvas, normalizedX, normalizedY, -1.0f);
        Vector3f far = unproject(clipToCanvas, normalizedX, normalizedY, 1.0f);
        float depthSpan = far.z() - near.z();
        if (Math.abs(depthSpan) < 1.0e-6f) {
            return Optional.empty();
        }
        float along = -near.z() / depthSpan;
        if (along < 0.0f || along > 1.0f) {
            return Optional.empty();
        }
        return Optional.of(new Vector2f(near.x() + (far.x() - near.x()) * along,
                near.y() + (far.y() - near.y()) * along));
    }

    private static Vector3f unproject(Matrix4f clipToCanvas, float x, float y, float depth) {
        Vector4f point = clipToCanvas.transform(new Vector4f(x, y, depth, 1.0f));
        return new Vector3f(point.x(), point.y(), point.z()).div(point.w());
    }
}
