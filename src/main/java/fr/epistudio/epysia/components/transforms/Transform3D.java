package fr.epistudio.epysia.components.transforms;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EpysiaComponent(name = "Transform 3D", category = "Core")
public final class Transform3D extends Component {

    @Export(label = "Position")
    private final Vector3f position = new Vector3f();
    @Export(label = "Rotation")
    private final Quaternionf rotation = new Quaternionf();
    @Export(label = "Scale")
    private final Vector3f scale = new Vector3f(1.0f, 1.0f, 1.0f);
    private final Matrix4f cachedLocalMatrix = new Matrix4f();
    private final Matrix4f cachedWorldMatrix = new Matrix4f();
    private final Vector3f previousPosition = new Vector3f();
    private final Quaternionf previousRotation = new Quaternionf();
    private final Vector3f previousScale = new Vector3f(1.0f, 1.0f, 1.0f);
    private final Vector3f blendedPosition = new Vector3f();
    private final Quaternionf blendedRotation = new Quaternionf();
    private final Vector3f blendedScale = new Vector3f();
    private final Matrix4f blendedWorldMatrix = new Matrix4f();
    private final List<Transform3D> children = new ArrayList<>();
    private Transform3D parent;
    @Export(label = "Render Layer", step = 1.0f)
    private int renderLayer;
    @Export(label = "Visible")
    private boolean visible = true;
    private boolean localDirty = true;
    private boolean worldDirty = true;
    private boolean blendDirty = true;
    private boolean localIdle;
    private static boolean idleFlagEnabled =
            Boolean.parseBoolean(System.getProperty("epysia.transform.idleFlag", "true"));

    public static void setIdleFlagEnabled(boolean value) {
        idleFlagEnabled = value;
    }
    private float blendedAlpha = Float.NaN;
    private boolean previousStateCaptured;
    private long worldVersion;

    public Vector3f position() {
        return position;
    }

    public Transform3D setPosition(float x, float y, float z) {
        position.set(x, y, z);
        markDirty();
        return this;
    }

    public Transform3D translate(float deltaX, float deltaY, float deltaZ) {
        position.add(deltaX, deltaY, deltaZ);
        markDirty();
        return this;
    }

    public Quaternionf rotation() {
        return rotation;
    }

    public Transform3D setRotation(Quaternionf source) {
        rotation.set(source).normalize();
        markDirty();
        return this;
    }

    public Transform3D setRotationEuler(float pitchRadians, float yawRadians, float rollRadians) {
        rotation.identity().rotateXYZ(pitchRadians, yawRadians, rollRadians);
        markDirty();
        return this;
    }

    public Transform3D rotateAxisAngle(float axisX, float axisY, float axisZ, float radians) {
        rotation.rotateAxis(radians, axisX, axisY, axisZ).normalize();
        markDirty();
        return this;
    }

    public Transform3D lookAt(float targetX, float targetY, float targetZ, float upX, float upY, float upZ) {
        rotation.identity()
                .lookAlong(targetX - position.x, targetY - position.y, targetZ - position.z, upX, upY, upZ)
                .invert();
        markDirty();
        return this;
    }

    public Vector3f scale() {
        return scale;
    }

    public Transform3D setScale(float x, float y, float z) {
        scale.set(x, y, z);
        markDirty();
        return this;
    }

    public void markDirty() {
        localDirty = true;
        markWorldDirty();
    }

    private void markWorldDirty() {
        localIdle = false;
        boolean alreadyPropagated = worldDirty && blendDirty;
        blendDirty = true;
        worldVersion++;
        if (alreadyPropagated) {
            return;
        }
        worldDirty = true;
        for (Transform3D child : children) {
            child.markWorldDirty();
        }
    }

    public long worldVersion() {
        return worldVersion;
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

    public Optional<Transform3D> parent() {
        return Optional.ofNullable(parent);
    }

    public List<Transform3D> children() {
        return children;
    }

    public boolean setParent(Transform3D newParent) {
        if (newParent == this || createsCycle(newParent)) {
            return false;
        }
        if (parent != null) {
            parent.children.remove(this);
        }
        parent = newParent;
        if (parent != null) {
            parent.children.add(this);
        }
        markWorldDirty();
        return true;
    }

    public void detachFromParent() {
        setParent(null);
    }

    private boolean createsCycle(Transform3D candidate) {
        Transform3D walker = candidate;
        while (walker != null) {
            if (walker == this) {
                return true;
            }
            walker = walker.parent;
        }
        return false;
    }

    public Matrix4f localMatrix() {
        if (localDirty) {
            cachedLocalMatrix.translationRotateScale(position, rotation, scale);
            localDirty = false;
        }
        return cachedLocalMatrix;
    }

    public Matrix4f worldMatrix() {
        if (!worldDirty) {
            return cachedWorldMatrix;
        }
        if (parent == null) {
            cachedWorldMatrix.set(localMatrix());
        } else {
            parent.worldMatrix().mul(localMatrix(), cachedWorldMatrix);
        }
        worldDirty = false;
        return cachedWorldMatrix;
    }

    public Vector3f worldPosition(Vector3f destination) {
        return worldMatrix().getTranslation(destination);
    }

    public Quaternionf worldRotation(Quaternionf destination) {
        return worldMatrix().getNormalizedRotation(destination);
    }

    public void captureInterpolationSnapshot() {
        if (previousStateCaptured && unchangedSincePreviousState()) {
            return;
        }
        previousPosition.set(position);
        previousRotation.set(rotation);
        previousScale.set(scale);
        previousStateCaptured = true;
        localIdle = true;
        blendDirty = true;
    }

    public Matrix4f worldMatrix(float alpha) {
        if (alpha >= 1.0f || !previousStateCaptured || hierarchyInterpolationIdle()) {
            return worldMatrix();
        }
        if (!blendDirty && blendedAlpha == alpha) {
            return blendedWorldMatrix;
        }
        blendLocalInto(blendedWorldMatrix, alpha);
        if (parent != null) {
            parent.worldMatrix(alpha).mul(blendedWorldMatrix, blendedWorldMatrix);
        }
        blendedAlpha = alpha;
        blendDirty = false;
        return blendedWorldMatrix;
    }

    public boolean worldMatrixStable(float alpha) {
        return alpha >= 1.0f || !previousStateCaptured || hierarchyInterpolationIdle();
    }

    private boolean hierarchyInterpolationIdle() {
        return localInterpolationIdle() && (parent == null || parent.hierarchyInterpolationIdle());
    }

    private boolean localInterpolationIdle() {
        return !previousStateCaptured || unchangedSincePreviousState();
    }

    private boolean unchangedSincePreviousState() {
        if (idleFlagEnabled) {
            return localIdle;
        }
        return previousPosition.equals(position)
                && previousRotation.equals(rotation)
                && previousScale.equals(scale);
    }

    private void blendLocalInto(Matrix4f destination, float alpha) {
        blendedPosition.set(previousPosition).lerp(position, alpha);
        previousRotation.slerp(rotation, alpha, blendedRotation);
        blendedScale.set(previousScale).lerp(scale, alpha);
        destination.translationRotateScale(blendedPosition, blendedRotation, blendedScale);
    }
}
