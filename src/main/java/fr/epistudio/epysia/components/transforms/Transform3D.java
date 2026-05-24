package fr.epistudio.epysia.components.transforms;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EpysiaComponent(name = "Transform 3D", category = "Core")
public final class Transform3D extends Component {

    @Export(label = "Position")
    private final Vector3f position = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    @Export(label = "Scale")
    private final Vector3f scale = new Vector3f(1.0f, 1.0f, 1.0f);
    private final Matrix4f cachedLocalMatrix = new Matrix4f();
    private int renderLayer;
    private boolean visible = true;
    private boolean matrixDirty = true;

    public Vector3f position() {
        return position;
    }

    public Transform3D setPosition(float x, float y, float z) {
        position.set(x, y, z);
        matrixDirty = true;
        return this;
    }

    public Transform3D translate(float deltaX, float deltaY, float deltaZ) {
        position.add(deltaX, deltaY, deltaZ);
        matrixDirty = true;
        return this;
    }

    public Quaternionf rotation() {
        return rotation;
    }

    public Transform3D setRotation(Quaternionf source) {
        rotation.set(source).normalize();
        matrixDirty = true;
        return this;
    }

    public Transform3D setRotationEuler(float pitchRadians, float yawRadians, float rollRadians) {
        rotation.identity().rotateXYZ(pitchRadians, yawRadians, rollRadians);
        matrixDirty = true;
        return this;
    }

    public Transform3D rotateAxisAngle(float axisX, float axisY, float axisZ, float radians) {
        rotation.rotateAxis(radians, axisX, axisY, axisZ).normalize();
        matrixDirty = true;
        return this;
    }

    public Transform3D lookAt(float targetX, float targetY, float targetZ, float upX, float upY, float upZ) {
        rotation.identity()
                .lookAlong(targetX - position.x, targetY - position.y, targetZ - position.z, upX, upY, upZ)
                .invert();
        matrixDirty = true;
        return this;
    }

    public Vector3f scale() {
        return scale;
    }

    public Transform3D setScale(float x, float y, float z) {
        scale.set(x, y, z);
        matrixDirty = true;
        return this;
    }

    public Transform3D setUniformScale(float value) {
        return setScale(value, value, value);
    }

    public int renderLayer() {
        return renderLayer;
    }

    public Transform3D setRenderLayer(int renderLayer) {
        this.renderLayer = renderLayer;
        return this;
    }

    public boolean visible() {
        return visible;
    }

    public Transform3D setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public Matrix4f localMatrix() {
        cachedLocalMatrix.translationRotateScale(position, rotation, scale);
        matrixDirty = false;
        return cachedLocalMatrix;
    }
}
