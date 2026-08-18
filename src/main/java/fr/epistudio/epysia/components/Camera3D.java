package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.postfx.PostEffectStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.Optional;

@EpysiaComponent(name = "Camera 3D", category = "Rendering",
        description = "Renders the scene from this object. One camera is primary at a time.")
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
    @Export(label = "Orthographic")
    private boolean orthographic;
    @Export(label = "Pixel Snap")
    private boolean pixelSnap;
    @Export(label = "Cull Mask", layerMask = true)
    private int cullMask = RenderLayers.ALL;
    @Export(label = "Ortho Size", min = 0.01f, max = 1000.0f, step = 0.1f)
    private float orthographicSize = 5.0f;
    @Export(label = "Pixels Per Unit", min = 0.0f, max = 512.0f, step = 1.0f)
    private float pixelsPerUnit;
    private float aspectRatio = 16.0f / 9.0f;
    private final PostEffectStack postEffectOverrideStack = new PostEffectStack();
    private boolean postEffectOverrideEnabled;
    private float projectionFieldOfViewDegrees = Float.NaN;
    private float projectionNearPlane = Float.NaN;
    private float projectionFarPlane = Float.NaN;
    private float projectionAspectRatio = Float.NaN;
    private boolean projectionOrthographic;
    private float projectionOrthographicSize = Float.NaN;
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f projectionOverride = new Matrix4f();
    private boolean projectionOverridden;
    private final Matrix4f viewProjectionMatrix = new Matrix4f();
    private final Matrix4f cullingViewProjectionMatrix = new Matrix4f();
    private transient int renderHeightPixels;

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

    public int cullMask() {
        return cullMask;
    }

    public Camera3D setCullMask(int cullMask) {
        this.cullMask = cullMask;
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

    public boolean orthographic() {
        return orthographic;
    }

    public Camera3D setOrthographic(boolean orthographic) {
        this.orthographic = orthographic;
        return this;
    }

    public float orthographicSize() {
        return orthographicSize;
    }

    public Camera3D setOrthographicSize(float size) {
        this.orthographicSize = size;
        return this;
    }

    public Optional<PostEffectStack> postEffectStack() {
        return postEffectOverrideEnabled ? Optional.of(postEffectOverrideStack) : Optional.empty();
    }

    public PostEffectStack enablePostEffectStack() {
        postEffectOverrideEnabled = true;
        return postEffectOverrideStack;
    }

    public void disablePostEffectStack() {
        postEffectOverrideEnabled = false;
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
        requireOwnerTransform().worldMatrix(interpolationAlpha).invert(viewMatrix);
        return snapToPixelGrid(viewMatrix);
    }

    public boolean pixelSnap() {
        return pixelSnap;
    }

    public Camera3D setPixelSnap(boolean value) {
        pixelSnap = value;
        return this;
    }

    public Camera3D setRenderHeightPixels(int value) {
        renderHeightPixels = Math.max(0, value);
        return this;
    }

    public float pixelsPerUnit() {
        return pixelsPerUnit;
    }

    public Camera3D setPixelsPerUnit(float value) {
        pixelsPerUnit = Math.max(0.0f, value);
        return this;
    }

    private float effectiveOrthographicSize() {
        if (pixelsPerUnit <= 0.0f || renderHeightPixels <= 0) {
            return orthographicSize;
        }
        return renderHeightPixels / (2.0f * pixelsPerUnit);
    }

    private Matrix4f snapToPixelGrid(Matrix4f view) {
        if (!pixelSnap || !orthographic || renderHeightPixels <= 0) {
            return view;
        }
        float step = 2.0f * effectiveOrthographicSize() / renderHeightPixels;
        view.m30(Math.round(view.m30() / step) * step);
        view.m31(Math.round(view.m31() / step) * step);
        return view;
    }

    public Camera3D setProjection(Matrix4fc matrix) {
        projectionOverride.set(matrix);
        projectionOverridden = true;
        return this;
    }

    public Camera3D clearProjectionOverride() {
        projectionOverridden = false;
        return this;
    }

    public boolean projectionOverridden() {
        return projectionOverridden;
    }

    public Matrix4f projection() {
        return projectionOverridden ? projectionOverride : cullingProjection();
    }

    public Matrix4f cullingProjection() {
        if (projectionMatchesSettings()) {
            return projectionMatrix;
        }
        projectionFieldOfViewDegrees = fieldOfViewDegrees;
        projectionAspectRatio = aspectRatio;
        projectionNearPlane = nearPlane;
        projectionFarPlane = farPlane;
        projectionOrthographic = orthographic;
        projectionOrthographicSize = effectiveOrthographicSize();
        if (orthographic) {
            float halfHeight = effectiveOrthographicSize();
            float halfWidth = halfHeight * aspectRatio;
            return projectionMatrix.identity().setOrtho(-halfWidth, halfWidth,
                    -halfHeight, halfHeight, nearPlane, farPlane);
        }
        return projectionMatrix.identity().perspective(
                (float) Math.toRadians(fieldOfViewDegrees),
                aspectRatio,
                nearPlane,
                farPlane
        );
    }

    private boolean projectionMatchesSettings() {
        return projectionFieldOfViewDegrees == fieldOfViewDegrees && projectionAspectRatio == aspectRatio
                && projectionNearPlane == nearPlane && projectionFarPlane == farPlane
                && projectionOrthographic == orthographic
                && projectionOrthographicSize == effectiveOrthographicSize();
    }

    public Matrix4f viewProjection() {
        return viewProjection(CURRENT_STATE_ALPHA);
    }

    public Matrix4f viewProjection(float interpolationAlpha) {
        return projection().mul(view(interpolationAlpha), viewProjectionMatrix);
    }

    public Matrix4f cullingViewProjection(float interpolationAlpha) {
        return cullingProjection().mul(view(interpolationAlpha), cullingViewProjectionMatrix);
    }

    private Transform3D requireOwnerTransform() {
        return owner()
                .orElseThrow(() -> new EpysiaException("Camera3D is not attached to a GameObject."))
                .getComponent(Transform3D.class)
                .orElseThrow(() -> new EpysiaException("Camera3D requires a Transform3D on the same GameObject."));
    }
}
