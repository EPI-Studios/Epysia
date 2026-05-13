package fr.epistudio.epysia.components.transforms;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.utils.Matrix3f;
import fr.epistudio.epysia.utils.Matrix4f;
import fr.epistudio.epysia.utils.Quaternionf;
import fr.epistudio.epysia.utils.Vector3f;

/**
 * Component that stores a full 3D transform.
 *
 * <p>The transform is expressed with a position, quaternion rotation, scale, visibility flag, and
 * render layer. Euler angles are exposed for convenience and are expressed in radians.
 */
public class Transform3D extends Component implements Transform {

    private Vector3f position;
    private Quaternionf rotation;
    private Vector3f scale;
    private boolean visible;
    private int renderLayer;

    /**
     * Creates a default 3D transform.
     */
    public Transform3D() {
        this(Vector3f.ZERO, Quaternionf.IDENTITY, Vector3f.ONE, true, 0);
    }

    /**
     * Creates a transform with a position.
     *
     * @param position local position
     */
    public Transform3D(Vector3f position) {
        this(position, Quaternionf.IDENTITY, Vector3f.ONE, true, 0);
    }

    /**
     * Creates a transform with position, Euler rotation, and scale.
     *
     * @param position local position
     * @param rotation Euler rotation in radians
     * @param scale local scale
     */
    public Transform3D(Vector3f position, Vector3f rotation, Vector3f scale) {
        this(position, Quaternionf.fromEuler(rotation.getX(), rotation.getY(), rotation.getZ()), scale, true, 0);
    }

    /**
     * Creates a transform with all fields explicitly specified.
     *
     * @param position local position
     * @param rotation quaternion rotation
     * @param scale local scale
     * @param visible visibility flag
     * @param renderLayer render ordering layer
     */
    public Transform3D(Vector3f position, Quaternionf rotation, Vector3f scale, boolean visible, int renderLayer) {
        this.position = position;
        this.rotation = rotation.normalize();
        this.scale = scale;
        this.visible = visible;
        this.renderLayer = renderLayer;
    }

    /**
     * Returns the position.
     *
     * @return current position
     */
    public Vector3f getPosition() {
        return position;
    }

    /**
     * Sets the position.
     *
     * @param position new position
     */
    public void setPosition(Vector3f position) {
        this.position = position;
    }

    /**
     * Moves the transform by an offset.
     *
     * @param delta translation offset
     */
    public void translate(Vector3f delta) {
        position = position.add(delta);
    }

    /**
     * Moves the transform by scalar offsets.
     *
     * @param dx x offset
     * @param dy y offset
     * @param dz z offset
     */
    public void translate(float dx, float dy, float dz) {
        position = position.add(new Vector3f(dx, dy, dz));
    }

    /**
     * Returns the rotation as Euler angles in radians.
     *
     * @return Euler rotation in radians
     */
    public Vector3f getRotation() {
        return quaternionToEuler(rotation);
    }

    /**
     * Returns the quaternion rotation.
     *
     * @return current quaternion rotation
     */
    public Quaternionf getRotationQuaternion() {
        return rotation;
    }

    /**
     * Sets the rotation from Euler angles expressed in radians.
     *
     * @param rotation Euler rotation in radians
     */
    public void setRotation(Vector3f rotation) {
        this.rotation = Quaternionf.fromEuler(rotation.getX(), rotation.getY(), rotation.getZ());
    }

    /**
     * Sets the quaternion rotation.
     *
     * @param rotation quaternion rotation
     */
    public void setRotation(Quaternionf rotation) {
        this.rotation = rotation.normalize();
    }

    /**
     * Appends a quaternion rotation.
     *
     * @param delta quaternion rotation delta
     */
    public void rotate(Quaternionf delta) {
        rotation = rotation.multiply(delta).normalize();
    }

    /**
     * Appends a rotation around an axis.
     *
     * @param axis rotation axis
     * @param radians angle in radians
     */
    public void rotateAxisAngle(Vector3f axis, float radians) {
        rotate(Quaternionf.fromAxisAngle(axis, radians));
    }

    /**
     * Sets the rotation so the forward axis faces a target point.
     *
     * @param target target world position
     * @param up preferred up axis
     */
    public void lookAt(Vector3f target, Vector3f up) {
        Vector3f forward = target.subtract(position).normalize();
        Vector3f right = up.cross(forward).normalize();
        Vector3f correctedUp = forward.cross(right).normalize();

        rotation = Quaternionf.fromMatrix(new Matrix3f(
                right.getX(), correctedUp.getX(), forward.getX(),
                right.getY(), correctedUp.getY(), forward.getY(),
                right.getZ(), correctedUp.getZ(), forward.getZ()
        ));
    }

    /**
     * Returns the scale.
     *
     * @return current scale
     */
    public Vector3f getScale() {
        return scale;
    }

    /**
     * Sets the scale.
     *
     * @param scale new scale
     */
    public void setScale(Vector3f scale) {
        this.scale = scale;
    }

    /**
     * Sets a uniform scale.
     *
     * @param uniformScale scale value applied on all axes
     */
    public void setScale(float uniformScale) {
        this.scale = new Vector3f(uniformScale, uniformScale, uniformScale);
    }

    /**
     * Multiplies the current scale component-wise.
     *
     * @param factor scale multiplier
     */
    public void scaleBy(Vector3f factor) {
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
     * Returns the render layer.
     *
     * @return render layer
     */
    public int getRenderLayer() {
        return renderLayer;
    }

    /**
     * Sets the render layer.
     *
     * @param renderLayer new render layer
     */
    public void setRenderLayer(int renderLayer) {
        this.renderLayer = renderLayer;
    }

    /**
     * Returns the forward direction.
     *
     * @return normalized forward vector
     */
    public Vector3f getForward() {
        return rotation.rotate(Vector3f.UNIT_Z);
    }

    /**
     * Returns the right direction.
     *
     * @return normalized right vector
     */
    public Vector3f getRight() {
        return rotation.rotate(Vector3f.UNIT_X);
    }

    /**
     * Returns the up direction.
     *
     * @return normalized up vector
     */
    public Vector3f getUp() {
        return rotation.rotate(Vector3f.UNIT_Y);
    }

    /**
     * Builds the transform matrix.
     *
     * @return homogeneous transform matrix
     */
    public Matrix4f toMatrix() {
        return Matrix4f.trs(position, rotation, scale);
    }

    /**
     * Transforms a local point into world space.
     *
     * @param localPoint local point
     * @return transformed point
     */
    public Vector3f transformPoint(Vector3f localPoint) {
        return toMatrix().transformPoint(localPoint);
    }

    /**
     * Transforms a local direction into world space.
     *
     * @param localDirection local direction
     * @return transformed direction
     */
    public Vector3f transformDirection(Vector3f localDirection) {
        return toMatrix().transformDirection(localDirection);
    }

    /**
     * Transforms a world point back into local space.
     *
     * @param worldPoint world point
     * @return local point
     */
    public Vector3f inverseTransformPoint(Vector3f worldPoint) {
        return toMatrix().inverse().transformPoint(worldPoint);
    }

    /**
     * Resets the transform to its default values.
     */
    public void reset() {
        position = Vector3f.ZERO;
        rotation = Quaternionf.IDENTITY;
        scale = Vector3f.ONE;
        visible = true;
        renderLayer = 0;
    }

    private static Vector3f quaternionToEuler(Quaternionf quaternion) {
        Quaternionf normalized = quaternion.normalize();

        float sinrCosp = 2.0f * (normalized.getW() * normalized.getX() + normalized.getY() * normalized.getZ());
        float cosrCosp = 1.0f - 2.0f * (normalized.getX() * normalized.getX() + normalized.getY() * normalized.getY());
        float pitch = (float) Math.atan2(sinrCosp, cosrCosp);

        float sinp = 2.0f * (normalized.getW() * normalized.getY() - normalized.getZ() * normalized.getX());
        float yaw;
        if (Math.abs(sinp) >= 1.0f) {
            yaw = (float) Math.copySign(Math.PI / 2.0, sinp);
        } else {
            yaw = (float) Math.asin(sinp);
        }

        float sinyCosp = 2.0f * (normalized.getW() * normalized.getZ() + normalized.getX() * normalized.getY());
        float cosyCosp = 1.0f - 2.0f * (normalized.getY() * normalized.getY() + normalized.getZ() * normalized.getZ());
        float roll = (float) Math.atan2(sinyCosp, cosyCosp);

        return new Vector3f(pitch, yaw, roll);
    }
}
