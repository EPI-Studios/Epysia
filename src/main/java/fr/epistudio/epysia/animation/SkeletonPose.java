package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Matrix4f;

import java.util.List;

public final class SkeletonPose {

    private final JointPose[] jointPoses;
    private final Matrix4f[] globalMatrices;
    private final Matrix4f localScratch = new Matrix4f();
    private final Matrix4f inverseBindScratch = new Matrix4f();

    public SkeletonPose(int jointCount) {
        if (jointCount <= 0) {
            throw new EpysiaException("SkeletonPose requires a positive joint count: " + jointCount);
        }
        jointPoses = new JointPose[jointCount];
        globalMatrices = new Matrix4f[jointCount];
        for (int index = 0; index < jointCount; index++) {
            jointPoses[index] = new JointPose();
            globalMatrices[index] = new Matrix4f();
        }
    }

    public int jointCount() {
        return jointPoses.length;
    }

    public JointPose jointPose(int index) {
        return jointPoses[index];
    }

    public void blendFrom(SkeletonPose from, float alpha) {
        if (from.jointPoses.length != jointPoses.length) {
            throw new EpysiaException("Cannot blend poses with mismatched joint counts.");
        }
        for (int index = 0; index < jointPoses.length; index++) {
            jointPoses[index].blendFrom(from.jointPoses[index], alpha);
        }
    }

    public void computeSkinningMatrices(Skeleton skeleton, Matrix4f[] out) {
        List<Joint> joints = skeleton.joints();
        if (joints.size() != jointPoses.length || out.length < joints.size()) {
            throw new EpysiaException("Skinning matrix arrays do not match the pose joint count.");
        }
        for (int index = 0; index < joints.size(); index++) {
            composeGlobal(joints.get(index), index);
            inverseBindScratch.set(joints.get(index).inverseBindMatrix());
            out[index].set(globalMatrices[index]).mul(inverseBindScratch);
        }
    }

    private void composeGlobal(Joint joint, int index) {
        JointPose pose = jointPoses[index];
        localScratch.translationRotateScale(pose.translation(), pose.rotation(), pose.scale());
        if (joint.parentIndex() < 0) {
            globalMatrices[index].set(localScratch);
        } else {
            globalMatrices[index].set(globalMatrices[joint.parentIndex()]).mul(localScratch);
        }
    }
}
