package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@EpysiaComponent(name = "Camera 3D", category = "Rendering")
@RequiresComponent(Transform3D.class)
public final class Camera3D extends Component {

    @Export(label = "Active")
    private boolean active = true;
    @Export(label = "FOV", min = 10.0f, max = 170.0f, step = 1.0f)
    private float fieldOfViewDegrees = 60.0f;
    @Export(label = "Near", min = 0.01f, max = 10.0f, step = 0.01f)
    private float nearPlane = 0.1f;
    @Export(label = "Far", min = 1.0f, max = 5000.0f, step = 1.0f)
    private float farPlane = 100.0f;
    private float aspectRatio = 16.0f / 9.0f;
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f viewProjectionMatrix = new Matrix4f();

    public Camera3D setFieldOfViewDegrees(float degrees) {
        this.fieldOfViewDegrees = degrees;
        return this;
    }

    public Camera3D setAspectRatio(float aspect) {
        this.aspectRatio = aspect;
        return this;
    }

    public Camera3D setNearFar(float near, float far) {
        this.nearPlane = near;
        this.farPlane = far;
        return this;
    }

    public boolean active() {
        return active;
    }

    public Camera3D setActive(boolean active) {
        this.active = active;
        return this;
    }

    public float fieldOfViewDegrees() {
        return fieldOfViewDegrees;
    }

    public float aspectRatio() {
        return aspectRatio;
    }

    public float nearPlane() {
        return nearPlane;
    }

    public float farPlane() {
        return farPlane;
    }

    public static final float CURRENT_STATE_ALPHA = 1.0f;

    public Vector3f position(Vector3f destination) {
        return destination.set(requireOwnerTransform().position());
    }

    public Vector3f position(Vector3f destination, float interpolationAlpha) {
        return requireOwnerTransform().worldMatrix(interpolationAlpha).getTranslation(destination);
    }

    public Matrix4f view() {
        return view(CURRENT_STATE_ALPHA);
    }

    public Matrix4f view(float interpolationAlpha) {
        return requireOwnerTransform().worldMatrix(interpolationAlpha).invert(viewMatrix);
    }

    public Matrix4f projection() {
        return projectionMatrix.identity().perspective(
                (float) Math.toRadians(fieldOfViewDegrees),
                aspectRatio,
                nearPlane,
                farPlane
        );
    }

    public Matrix4f viewProjection() {
        return viewProjection(CURRENT_STATE_ALPHA);
    }

    public Matrix4f viewProjection(float interpolationAlpha) {
        return projection().mul(view(interpolationAlpha), viewProjectionMatrix);
    }

    private Transform3D requireOwnerTransform() {
        return owner()
                .orElseThrow(() -> new EpysiaException("Camera3D is not attached to a GameObject."))
                .getComponent(Transform3D.class)
                .orElseThrow(() -> new EpysiaException("Camera3D requires a Transform3D on the same GameObject."));
    }
}
