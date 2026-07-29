package fr.epistudio.epysia.components;

import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.animation.SkeletonPose;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EpysiaComponent(name = "Joint Socket", category = "Animation")
@RequiresComponent(Transform3D.class)
public final class JointSocket extends Component {

    @Export(label = "Joint")
    private String jointName = "";
    @Export(label = "Offset Position")
    private final Vector3f offsetPosition = new Vector3f();
    @Export(label = "Offset Rotation")
    private final Vector3f offsetEulerDegrees = new Vector3f();

    private final Matrix4f attachedMatrix = new Matrix4f();
    private final Quaternionf scratchRotation = new Quaternionf();
    private final Vector3f scratchTranslation = new Vector3f();
    private final Vector3f scratchScale = new Vector3f();
    private int resolvedIndex = -1;
    private long resolvedChecksum;

    public String jointName() {
        return jointName;
    }

    public JointSocket setJointName(String value) {
        this.jointName = value;
        this.resolvedIndex = -1;
        return this;
    }

    public JointSocket setOffsetPosition(float x, float y, float z) {
        offsetPosition.set(x, y, z);
        return this;
    }

    public JointSocket setOffsetRotationDegrees(float pitch, float yaw, float roll) {
        offsetEulerDegrees.set(pitch, yaw, roll);
        return this;
    }

    public int resolvedJointIndex() {
        return resolvedIndex;
    }

    public boolean resolve(Skeleton skeleton) {
        if (jointName.isEmpty()) {
            resolvedIndex = -1;
            return false;
        }
        if (resolvedIndex >= 0 && resolvedChecksum == skeleton.nameChecksum()) {
            return true;
        }
        resolvedIndex = skeleton.indexOfJoint(jointName);
        resolvedChecksum = skeleton.nameChecksum();
        return resolvedIndex >= 0;
    }

    public void applyPose(SkeletonPose pose) {
        Transform3D transform = ownerTransform();
        if (transform == null || resolvedIndex < 0 || resolvedIndex >= pose.jointCount()) {
            return;
        }
        attachedMatrix.set(pose.globalMatrix(resolvedIndex));
        applyOffset(attachedMatrix);
        writeInto(transform, attachedMatrix);
    }

    private void applyOffset(Matrix4f matrix) {
        matrix.translate(offsetPosition);
        matrix.rotateXYZ((float) Math.toRadians(offsetEulerDegrees.x),
                (float) Math.toRadians(offsetEulerDegrees.y),
                (float) Math.toRadians(offsetEulerDegrees.z));
    }

    private void writeInto(Transform3D transform, Matrix4f matrix) {
        matrix.getTranslation(scratchTranslation);
        matrix.getUnnormalizedRotation(scratchRotation);
        matrix.getScale(scratchScale);
        transform.setPosition(scratchTranslation.x, scratchTranslation.y, scratchTranslation.z);
        transform.setRotation(scratchRotation.normalize());
        transform.setScale(scratchScale.x, scratchScale.y, scratchScale.z);
    }

    private Transform3D ownerTransform() {
        GameObject owner = ownerOrNull();
        return owner == null ? null : owner.transform3DOrNull();
    }
}
