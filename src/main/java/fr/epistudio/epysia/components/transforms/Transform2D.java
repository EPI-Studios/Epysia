package fr.epistudio.epysia.components.transforms;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

@EpysiaComponent(name = "Transform 2D", category = "Core")
public final class Transform2D extends Component {

    @Export(label = "Position")
    private final Vector2f position = new Vector2f();
    @Export(label = "Scale")
    private final Vector2f scale = new Vector2f(1.0f, 1.0f);
    @Export(label = "Pivot")
    private final Vector2f pivot = new Vector2f();
    private final Matrix3x2f cachedLocalMatrix = new Matrix3x2f();
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
        matrixDirty = true;
        return this;
    }

    public Transform2D translate(float deltaX, float deltaY) {
        position.add(deltaX, deltaY);
        matrixDirty = true;
        return this;
    }

    public Vector2f scale() {
        return scale;
    }

    public Transform2D setScale(float x, float y) {
        scale.set(x, y);
        matrixDirty = true;
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
        matrixDirty = true;
        return this;
    }

    public float rotationRadians() {
        return rotationRadians;
    }

    public Transform2D setRotationRadians(float radians) {
        this.rotationRadians = radians;
        matrixDirty = true;
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
    }

    public Matrix3x2f localMatrix() {
        if (matrixDirty) {
            rebuildLocalMatrix();
            matrixDirty = false;
        }
        return cachedLocalMatrix;
    }

    private void rebuildLocalMatrix() {
        cachedLocalMatrix
                .translation(position)
                .translate(pivot)
                .rotate(rotationRadians)
                .scale(scale)
                .translate(-pivot.x, -pivot.y);
    }
}
