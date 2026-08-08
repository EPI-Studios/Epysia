package fr.epistudio.epysia.components.transforms;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EpysiaComponent(name = "Transform 2D", category = "Core")
public final class Transform2D extends Component {
    @Export(label = "Position")
    private final Vector2f position = new Vector2f();
    @Export(label = "Scale")
    private final Vector2f scale = new Vector2f(1.0f, 1.0f);
    @Export(label = "Pivot")
    private final Vector2f pivot = new Vector2f();
    private final Matrix3x2f cachedLocalMatrix = new Matrix3x2f();
    private final Matrix3x2f cachedWorldMatrix = new Matrix3x2f();
    private final List<Transform2D> children = new ArrayList<>();
    private Transform2D parent;
    private boolean worldDirty = true;
    @Export(label = "Rotation Radians", step = 0.01f)
    private float rotationRadians;
    @Export(label = "Render Layer", step = 1.0f)
    private int renderLayer;
    @Export(label = "Visible")
    private boolean visible = true;
    private boolean matrixDirty = true;

    public Vector2f position() {
        return position;
    }

    public Transform2D setPosition(float x, float y) {
        position.set(x, y);
        markDirty();
        return this;
    }

    public Transform2D translate(float deltaX, float deltaY) {
        position.add(deltaX, deltaY);
        markDirty();
        return this;
    }

    public Vector2f scale() {
        return scale;
    }

    public Transform2D setScale(float x, float y) {
        scale.set(x, y);
        markDirty();
        return this;
    }

    public Transform2D setUniformScale(float value) {
        return setScale(value, value);
    }

    public Vector2f pivot() {
        return pivot;
    }

    public Transform2D setPivot(float x, float y) {
        pivot.set(x, y);
        markDirty();
        return this;
    }

    public float rotationRadians() {
        return rotationRadians;
    }

    public Transform2D setRotationRadians(float radians) {
        this.rotationRadians = radians;
        markDirty();
        return this;
    }

    public Transform2D rotate(float deltaRadians) {
        return setRotationRadians(rotationRadians + deltaRadians);
    }

    public int renderLayer() {
        return renderLayer;
    }

    public Transform2D setRenderLayer(int renderLayer) {
        this.renderLayer = renderLayer;
        return this;
    }

    public boolean visible() {
        return visible;
    }

    public Transform2D setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public void markDirty() {
        matrixDirty = true;
        markWorldDirty();
    }

    private void markWorldDirty() {
        if (worldDirty) {
            return;
        }
        worldDirty = true;
        for (int index = 0; index < children.size(); index++) {
            children.get(index).markWorldDirty();
        }
    }

    public Optional<Transform2D> parent() {
        return Optional.ofNullable(parent);
    }

    public List<Transform2D> children() {
        return children;
    }

    public boolean setParent(Transform2D newParent) {
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
        markDirty();
        return true;
    }

    public void detachFromParent() {
        setParent(null);
    }

    private boolean createsCycle(Transform2D candidate) {
        Transform2D walker = candidate;
        while (walker != null) {
            if (walker == this) {
                return true;
            }
            walker = walker.parent;
        }
        return false;
    }

    public Matrix3x2f localMatrix() {
        if (matrixDirty) {
            rebuildLocalMatrix();
            matrixDirty = false;
        }
        return cachedLocalMatrix;
    }

    public Matrix3x2f worldMatrix() {
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

    public Vector2f worldPosition(Vector2f destination) {
        destination.set(position);
        if (parent == null) {
            return destination;
        }
        return parent.worldMatrix().transformPosition(destination);
    }

    public Transform2D setWorldPosition(float x, float y) {
        if (parent == null) {
            return setPosition(x, y);
        }
        Vector2f local = new Matrix3x2f(parent.worldMatrix()).invert()
                .transformPosition(new Vector2f(x, y));
        return setPosition(local.x, local.y);
    }

    public Vector2f worldOrigin(Vector2f destination) {
        return worldMatrix().transformPosition(destination.set(0.0f, 0.0f));
    }

    public Transform2D setWorldOrigin(float x, float y) {
        Vector2f local = new Vector2f(x, y);
        if (parent != null) {
            new Matrix3x2f(parent.worldMatrix()).invert().transformPosition(local);
        }
        Vector2f shift = new Matrix3x2f().rotate(rotationRadians).scale(scale)
                .transformPosition(new Vector2f(pivot));
        return setPosition(local.x + shift.x, local.y + shift.y);
    }

    public float worldRotationRadians() {
        Matrix3x2f world = worldMatrix();
        return (float) Math.atan2(world.m01(), world.m00());
    }

    public Vector2f worldScale(Vector2f destination) {
        Matrix3x2f world = worldMatrix();
        return destination.set(
                (float) Math.sqrt(world.m00() * world.m00() + world.m01() * world.m01()),
                (float) Math.sqrt(world.m10() * world.m10() + world.m11() * world.m11()));
    }

    public Transform2D setWorldScale(float x, float y) {
        if (parent == null) {
            return setScale(x, y);
        }
        Vector2f parentScale = parent.worldScale(new Vector2f());
        return setScale(parentScale.x == 0.0f ? x : x / parentScale.x,
                parentScale.y == 0.0f ? y : y / parentScale.y);
    }

    public Transform2D setWorldRotationRadians(float radians) {
        if (parent == null) {
            return setRotationRadians(radians);
        }
        return setRotationRadians(radians - parent.worldRotationRadians());
    }

    public Transform2D setLocalMatrix(Matrix3x2f matrix) {
        float scaleX = (float) Math.sqrt(matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01());
        float scaleY = (float) Math.sqrt(matrix.m10() * matrix.m10() + matrix.m11() * matrix.m11());
        rotationRadians = (float) Math.atan2(matrix.m01(), matrix.m00());
        scale.set(scaleX, scaleY);
        position.set(matrix.m20() + matrix.m00() * pivot.x + matrix.m10() * pivot.y,
                matrix.m21() + matrix.m01() * pivot.x + matrix.m11() * pivot.y);
        markDirty();
        return this;
    }

    private void rebuildLocalMatrix() {
        cachedLocalMatrix
                .translation(position)
                .rotate(rotationRadians)
                .scale(scale)
                .translate(-pivot.x, -pivot.y);
    }
}
