package fr.epistudio.epysia.animation;

import org.joml.Matrix4f;

public final class BindPose {
    private final Skeleton skeleton;
    private final SkeletonPose pose;

    public BindPose(Skeleton skeleton) {
        this.skeleton = skeleton;
        this.pose = new SkeletonPose(skeleton.jointCount());
        decomposeJoints();
    }

    private void decomposeJoints() {
        Matrix4f localBind = new Matrix4f();
        for (int index = 0; index < skeleton.jointCount(); index++) {
            localBind.set(skeleton.joints().get(index).localBindTransform());
            pose.jointPose(index).setFromMatrix(localBind);
        }
    }

    public Skeleton skeleton() {
        return skeleton;
    }

    public void copyInto(SkeletonPose out) {
        out.copyFrom(pose);
    }
}
