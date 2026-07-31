package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class PoseLayerBlend {

    private static final float MINIMUM_BIND_SCALE = 1.0e-5f;

    private final Matrix4f bindScratch = new Matrix4f();
    private final JointPose bindPose = new JointPose();
    private final Quaternionf jointDelta = new Quaternionf();
    private final Quaternionf weightedDelta = new Quaternionf();

    public void apply(SkeletonPose base, SkeletonPose layer, Skeleton skeleton,
                      AnimationBlendMode mode, float weight, JointMask mask) {
        if (base.jointCount() != layer.jointCount() || base.jointCount() != skeleton.jointCount()) {
            throw new EpysiaException("Cannot blend a layer whose joint count differs from the base pose.");
        }
        for (int index = 0; index < base.jointCount(); index++) {
            float jointWeight = weight * mask.weight(index);
            if (jointWeight <= 0.0f) {
                continue;
            }
            applyJoint(base.jointPose(index), layer.jointPose(index),
                    skeleton.joints().get(index), mode, jointWeight);
        }
    }

    private void applyJoint(JointPose base, JointPose layer, Joint joint,
                            AnimationBlendMode mode, float weight) {
        switch (mode) {
            case OVERRIDE -> base.blendToward(layer, Math.min(1.0f, weight));
            case ADDITIVE -> addJoint(base, layer, joint, weight);
        }
    }

    private void addJoint(JointPose base, JointPose layer, Joint joint, float weight) {
        bindScratch.set(joint.localBindTransform());
        bindPose.setFromMatrix(bindScratch);
        addTranslation(base, layer, weight);
        addRotation(base, layer, weight);
        addScale(base, layer, weight);
    }

    private void addTranslation(JointPose base, JointPose layer, float weight) {
        base.translation().add(
                (layer.translation().x - bindPose.translation().x) * weight,
                (layer.translation().y - bindPose.translation().y) * weight,
                (layer.translation().z - bindPose.translation().z) * weight);
    }

    private void addRotation(JointPose base, JointPose layer, float weight) {
        bindPose.rotation().invert(jointDelta).mul(layer.rotation()).normalize();
        if (jointDelta.w < 0.0f) {
            jointDelta.set(-jointDelta.x, -jointDelta.y, -jointDelta.z, -jointDelta.w);
        }
        weightedDelta.identity().nlerp(jointDelta, weight);
        base.rotation().mul(weightedDelta).normalize();
    }

    private void addScale(JointPose base, JointPose layer, float weight) {
        base.scale().set(
                scaledComponent(base.scale().x, layer.scale().x, bindPose.scale().x, weight),
                scaledComponent(base.scale().y, layer.scale().y, bindPose.scale().y, weight),
                scaledComponent(base.scale().z, layer.scale().z, bindPose.scale().z, weight));
    }

    private static float scaledComponent(float baseScale, float layerScale, float bindScale, float weight) {
        if (Math.abs(bindScale) < MINIMUM_BIND_SCALE) {
            return baseScale;
        }
        return baseScale * (1.0f + weight * (layerScale / bindScale - 1.0f));
    }
}
