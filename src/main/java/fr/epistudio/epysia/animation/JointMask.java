package fr.epistudio.epysia.animation;

public final class JointMask {

    private static final JointMask FULL = new JointMask(new float[0]);

    private final float[] weights;

    private JointMask(float[] weights) {
        this.weights = weights;
    }

    public static JointMask full() {
        return FULL;
    }

    public static JointMask subtree(Skeleton skeleton, String rootJointName) {
        int rootIndex = skeleton.indexOfJoint(rootJointName);
        float[] weights = new float[skeleton.jointCount()];
        if (rootIndex < 0) {
            return new JointMask(weights);
        }
        weights[rootIndex] = 1.0f;
        for (int index = rootIndex + 1; index < weights.length; index++) {
            int parentIndex = skeleton.joints().get(index).parentIndex();
            weights[index] = parentIndex >= 0 && weights[parentIndex] > 0.0f ? 1.0f : 0.0f;
        }
        return new JointMask(weights);
    }

    public boolean coversEveryJoint() {
        return weights.length == 0;
    }

    public float weight(int jointIndex) {
        if (coversEveryJoint()) {
            return 1.0f;
        }
        return jointIndex >= 0 && jointIndex < weights.length ? weights[jointIndex] : 0.0f;
    }
}
