package fr.epistudio.epysia.components;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EpysiaComponent(name = "Directional Light", category = "Lighting")
public final class DirectionalLight extends Light {

    private static final Vector3f LOCAL_FORWARD = new Vector3f(0.0f, 0.0f, -1.0f);

    @Export(label = "Ambient", color = true)
    private final Vector3f ambient = new Vector3f(0.18f, 0.20f, 0.26f);
    @Export(label = "Shadow Extent", min = 1.0f, max = 200.0f, step = 0.5f)
    private float shadowHalfExtent = 6.0f;
    @Export(label = "Shadow Near", min = 0.01f, max = 50.0f, step = 0.1f)
    private float shadowNear = 0.5f;
    @Export(label = "Shadow Far", min = 1.0f, max = 500.0f, step = 0.5f)
    private float shadowFar = 25.0f;
    private float shadowSourceDistance = 10.0f;
    private final Vector3f sceneCenter = new Vector3f();
    private final Matrix4f viewProjectionMatrix = new Matrix4f();
    private final Vector3f scratchDirection = new Vector3f();
    private final Vector3f scratchLightPosition = new Vector3f();
    private final Vector3f scratchUp = new Vector3f();
    private final Quaternionf scratchWorldRotation = new Quaternionf();

    @Override
    public DirectionalLight setColor(float red, float green, float blue) {
        super.setColor(red, green, blue);
        return this;
    }

    @Override
    public DirectionalLight setIntensity(float intensity) {
        super.setIntensity(intensity);
        return this;
    }

    public DirectionalLight setAmbient(float red, float green, float blue) {
        ambient.set(red, green, blue);
        return this;
    }

    public DirectionalLight setShadowExtent(float halfExtent, float near, float far) {
        this.shadowHalfExtent = halfExtent;
        this.shadowNear = near;
        this.shadowFar = far;
        return this;
    }

    public DirectionalLight setShadowSourceDistance(float distance) {
        this.shadowSourceDistance = distance;
        return this;
    }

    public DirectionalLight setSceneCenter(float x, float y, float z) {
        sceneCenter.set(x, y, z);
        return this;
    }

    public Vector3f ambient() {
        return ambient;
    }

    public Vector3f direction(Vector3f destination) {
        Transform3D transform = requireOwnerTransform();
        return transform.worldRotation(scratchWorldRotation).transform(LOCAL_FORWARD, destination);
    }

    public Matrix4f viewProjection() {
        direction(scratchDirection);
        scratchLightPosition.set(scratchDirection).negate().mul(shadowSourceDistance).add(sceneCenter);
        return viewProjectionMatrix
                .identity()
                .ortho(-shadowHalfExtent, shadowHalfExtent, -shadowHalfExtent, shadowHalfExtent, shadowNear, shadowFar)
                .lookAt(scratchLightPosition, sceneCenter, chooseUp(scratchDirection));
    }

    private Vector3f chooseUp(Vector3f direction) {
        if (Math.abs(direction.y) > 0.99f) {
            return scratchUp.set(0.0f, 0.0f, 1.0f);
        }
        return scratchUp.set(0.0f, 1.0f, 0.0f);
    }

    private Transform3D requireOwnerTransform() {
        return owner()
                .orElseThrow(() -> new EpysiaException("DirectionalLight is not attached to a GameObject."))
                .getComponent(Transform3D.class)
                .orElseThrow(() -> new EpysiaException("DirectionalLight requires a Transform3D on the same GameObject."));
    }
}
