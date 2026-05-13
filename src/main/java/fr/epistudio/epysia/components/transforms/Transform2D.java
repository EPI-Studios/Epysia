package fr.epistudio.epysia.components.transforms;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.utils.Matrix3f;
import fr.epistudio.epysia.utils.Vector2f;

/**
 * Component that stores a full 2D transform.
 *
 * <p>The transform is expressed with a position, scale, rotation in radians, pivot, render order,
 * and visibility flag.
 */
public class Transform2D extends Component implements Transform{

    private Vector2f position;
    private Vector2f scale;
    private Vector2f pivot;
    private float rotation;
    private int zIndex;
    private boolean visible;

    /**
     * Creates a transform located at the origin.
     */
    public Transform2D() {
        this(Vector2f.ZERO, Vector2f.ONE, 0.0f, 0, true, Vector2f.ZERO);
    }

    /**
     * Creates a transform with a position.
     *
     * @param position local position
     */
    public Transform2D(Vector2f position) {
        this(position, Vector2f.ONE, 0.0f, 0, true, Vector2f.ZERO);
    }

    /**
     * Creates a transform with a position and scale.
     *
     * @param position local position
     * @param scale local scale
     */
    public Transform2D(Vector2f position, Vector2f scale) {
        this(position, scale, 0.0f, 0, true, Vector2f.ZERO);
    }

    /**
     * Creates a transform with a position, scale, and rotation.
     *
     * @param position local position
     * @param scale local scale
     * @param rotation rotation in radians
     */
    public Transform2D(Vector2f position, Vector2f scale, float rotation) {
        this(position, scale, rotation, 0, true, Vector2f.ZERO);
    }

    /**
     * Creates a transform with a position, scale, rotation, and z index.
     *
     * @param position local position
     * @param scale local scale
     * @param rotation rotation in radians
     * @param zIndex render order
     */
    public Transform2D(Vector2f position, Vector2f scale, float rotation, int zIndex) {
        this(position, scale, rotation, zIndex, true, Vector2f.ZERO);
    }

    /**
     * Creates a transform with all fields explicitly specified.
     *
     * @param position local position
     * @param scale local scale
     * @param rotation rotation in radians
     * @param zIndex render order
     * @param visible visibility flag
     * @param pivot local pivot used for rotation and scaling
     */
    public Transform2D(Vector2f position, Vector2f scale, float rotation, int zIndex, boolean visible, Vector2f pivot) {
        this.position = position;
        this.scale = scale;
        this.rotation = rotation;
        this.zIndex = zIndex;
        this.visible = visible;
        this.pivot = pivot;
    }

    /**
     * Returns the position.
     *
     * @return current position
     */
    public Vector2f getPosition() {
        return position;
    }

    /**
     * Sets the position.
     *
     * @param position new position
     */
    public void setPosition(Vector2f position) {
        this.position = position;
    }

    /**
     * Moves the transform by an offset.
     *
     * @param delta translation offset
     */
    public void translate(Vector2f delta) {
        position = position.add(delta);
    }

    /**
     * Moves the transform by scalar offsets.
     *
     * @param dx x offset
     * @param dy y offset
     */
    public void translate(float dx, float dy) {
        position = position.add(dx, dy);
    }

    /**
     * Returns the scale.
     *
     * @return current scale
     */
    public Vector2f getScale() {
        return scale;
    }

    /**
     * Sets the scale.
     *
     * @param scale new scale
     */
    public void setScale(Vector2f scale) {
        this.scale = scale;
    }

    /**
     * Sets a uniform scale.
     *
     * @param uniformScale scale value applied on both axes
     */
    public void setScale(float uniformScale) {
        this.scale = new Vector2f(uniformScale, uniformScale);
    }

    /**
     * Multiplies the current scale component-wise.
     *
     * @param factor scale multiplier
     */
    public void scaleBy(Vector2f factor) {
        scale = scale.multiply(factor);
    }

    /**
     * Multiplies the current scale uniformly.
     *
     * @param factor uniform multiplier
     */
    public void scaleBy(float factor) {
        scale = scale.multiply(factor);
    }

    /**
     * Returns the pivot.
     *
     * @return current pivot
     */
    public Vector2f getPivot() {
        return pivot;
    }

    /**
     * Sets the pivot.
     *
     * @param pivot new pivot
     */
    public void setPivot(Vector2f pivot) {
        this.pivot = pivot;
    }

    /**
     * Returns the rotation in radians.
     *
     * @return current rotation
     */
    public float getRotation() {
        return rotation;
    }

    /**
     * Sets the rotation in radians.
     *
     * @param rotation new rotation
     */
    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    /**
     * Adds a relative rotation in radians.
     *
     * @param delta radians to add
     */
    public void rotate(float delta) {
        rotation += delta;
    }

    /**
     * Sets the rotation so the transform faces a target point.
     *
     * @param target target world position
     */
    public void lookAt(Vector2f target) {
        rotation = target.subtract(position).angle();
    }

    /**
     * Returns the z index.
     *
     * @return render order
     */
    public int getZIndex() {
        return zIndex;
    }

    /**
     * Sets the z index.
     *
     * @param zIndex new render order
     */
    public void setZIndex(int zIndex) {
        this.zIndex = zIndex;
    }

    /**
     * Returns whether the transform is visible.
     *
     * @return visibility flag
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Sets the visibility flag.
     *
     * @param visible new visibility
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Toggles visibility.
     */
    public void toggleVisible() {
        visible = !visible;
    }

    /**
     * Returns the right direction derived from the current rotation.
     *
     * @return normalized right vector
     */
    public Vector2f getRight() {
        return new Vector2f((float) Math.cos(rotation), (float) Math.sin(rotation));
    }

    /**
     * Returns the up direction derived from the current rotation.
     *
     * @return normalized up vector
     */
    public Vector2f getUp() {
        Vector2f right = getRight();
        return new Vector2f(-right.getY(), right.getX());
    }

    /**
     * Builds the transform matrix.
     *
     * @return homogeneous transform matrix
     */
    public Matrix3f toMatrix() {
        return Matrix3f.translation(position)
                .multiply(Matrix3f.translation(pivot))
                .multiply(Matrix3f.rotation(rotation))
                .multiply(Matrix3f.scale(scale))
                .multiply(Matrix3f.translation(pivot.negate()));
    }

    /**
     * Transforms a local point into world space.
     *
     * @param localPoint local point
     * @return transformed point
     */
    public Vector2f transformPoint(Vector2f localPoint) {
        return toMatrix().transformPoint(localPoint);
    }

    /**
     * Transforms a local direction into world space.
     *
     * @param localDirection local direction
     * @return transformed direction
     */
    public Vector2f transformDirection(Vector2f localDirection) {
        return toMatrix().transformDirection(localDirection);
    }

    /**
     * Transforms a world point back into local space.
     *
     * @param worldPoint world point
     * @return local point
     */
    public Vector2f inverseTransformPoint(Vector2f worldPoint) {
        return toMatrix().inverse().transformPoint(worldPoint);
    }

    /**
     * Resets the transform to its default values.
     */
    public void reset() {
        position = Vector2f.ZERO;
        scale = Vector2f.ONE;
        pivot = Vector2f.ZERO;
        rotation = 0.0f;
        zIndex = 0;
        visible = true;
    }
}
