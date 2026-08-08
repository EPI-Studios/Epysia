package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipSamplerTest {
    private static float[] identity() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static Joint joint(String name, int parent) {
        return new Joint(name, parent, identity(), identity());
    }

    private static Skeleton twoJointSkeleton() {
        return new Skeleton(List.of(joint("root", -1), joint("child", 0)));
    }

    private static Matrix4f[] allocateMatrices(int jointCount) {
        Matrix4f[] matrices = new Matrix4f[jointCount];
        for (int index = 0; index < jointCount; index++) {
            matrices[index] = new Matrix4f();
        }
        return matrices;
    }

    private static Matrix4f[] sampleToMatrices(Clip clip, Skeleton skeleton, float time) {
        SkeletonPose pose = new SkeletonPose(skeleton.jointCount());
        new ClipSampler().sample(clip, new BindPose(skeleton), time, pose);
        Matrix4f[] matrices = allocateMatrices(skeleton.jointCount());
        pose.computeSkinningMatrices(skeleton, matrices);
        return matrices;
    }

    @Test
    void identityClipYieldsIdentityPalette() {
        Skeleton skeleton = twoJointSkeleton();
        ClipChannel channel = new ClipChannel(0, ClipProperty.TRANSLATION, ClipInterpolation.STEP,
                new float[]{0.0f}, new float[]{0.0f, 0.0f, 0.0f});
        Clip clip = new Clip("identity", 1.0f, skeleton.nameChecksum(), List.of(channel));

        Matrix4f[] matrices = sampleToMatrices(clip, skeleton, 0.0f);

        ByteBuffer target = ByteBuffer.allocate(matrices.length * SkinningPalette.BYTES_PER_JOINT)
                .order(ByteOrder.nativeOrder());
        SkinningPalette.pack(matrices, target);
        float[] expected = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};
        for (int joint = 0; joint < matrices.length; joint++) {
            for (int component = 0; component < expected.length; component++) {
                assertEquals(expected[component], target.getFloat((joint * 12 + component) * 4), 1e-6f);
            }
        }
    }

    @Test
    void translationChannelSurvivesToSkinningMatrix() {
        Skeleton skeleton = twoJointSkeleton();
        ClipChannel channel = new ClipChannel(0, ClipProperty.TRANSLATION, ClipInterpolation.LINEAR,
                new float[]{0.0f, 1.0f}, new float[]{0, 0, 0, 0, 2, 0});
        Clip clip = new Clip("translate", 1.0f, skeleton.nameChecksum(), List.of(channel));

        Matrix4f[] matrices = sampleToMatrices(clip, skeleton, 1.0f);

        Vector4f rowOne = new Vector4f();
        matrices[0].getRow(1, rowOne);
        assertEquals(2.0f, rowOne.w, 1e-6f);
    }

    @Test
    void childInheritsParentRotation() {
        Skeleton skeleton = twoJointSkeleton();
        Quaternionf ninety = new Quaternionf().rotateZ((float) java.lang.Math.toRadians(90.0));
        ClipChannel channel = new ClipChannel(0, ClipProperty.ROTATION, ClipInterpolation.STEP,
                new float[]{0.0f}, new float[]{ninety.x, ninety.y, ninety.z, ninety.w});
        Clip clip = new Clip("rotate", 1.0f, skeleton.nameChecksum(), List.of(channel));

        Matrix4f[] matrices = sampleToMatrices(clip, skeleton, 0.0f);

        Vector3f transformed = matrices[1].transformPosition(new Vector3f(1, 0, 0));
        assertEquals(0.0f, transformed.x, 1e-5f);
        assertEquals(1.0f, transformed.y, 1e-5f);
        assertEquals(0.0f, transformed.z, 1e-5f);
    }

    @Test
    void stepAndLinearDifferAtMidpoint() {
        Skeleton skeleton = twoJointSkeleton();
        float[] times = {0.0f, 1.0f};
        float[] values = {0, 0, 0, 0, 4, 0};
        Clip stepClip = new Clip("step", 1.0f, skeleton.nameChecksum(),
                List.of(new ClipChannel(0, ClipProperty.TRANSLATION, ClipInterpolation.STEP, times, values)));
        Clip linearClip = new Clip("linear", 1.0f, skeleton.nameChecksum(),
                List.of(new ClipChannel(0, ClipProperty.TRANSLATION, ClipInterpolation.LINEAR, times, values)));

        SkeletonPose stepPose = new SkeletonPose(skeleton.jointCount());
        SkeletonPose linearPose = new SkeletonPose(skeleton.jointCount());
        new ClipSampler().sample(stepClip, new BindPose(skeleton), 0.5f, stepPose);
        new ClipSampler().sample(linearClip, new BindPose(skeleton), 0.5f, linearPose);

        assertEquals(0.0f, stepPose.jointPose(0).translation().y, 1e-6f);
        assertEquals(2.0f, linearPose.jointPose(0).translation().y, 1e-6f);
        assertNotEquals(stepPose.jointPose(0).translation().y, linearPose.jointPose(0).translation().y);
    }

    @Test
    void rotationLerpAtMidpointYieldsNinetyDegrees() {
        Skeleton skeleton = twoJointSkeleton();
        Quaternionf oneEighty = new Quaternionf().rotateZ((float) java.lang.Math.toRadians(180.0));
        ClipChannel channel = new ClipChannel(0, ClipProperty.ROTATION, ClipInterpolation.LINEAR,
                new float[]{0.0f, 1.0f},
                new float[]{0, 0, 0, 1, oneEighty.x, oneEighty.y, oneEighty.z, oneEighty.w});
        Clip clip = new Clip("rotate", 1.0f, skeleton.nameChecksum(), List.of(channel));

        SkeletonPose pose = new SkeletonPose(skeleton.jointCount());
        new ClipSampler().sample(clip, new BindPose(skeleton), 0.5f, pose);

        Quaternionf ninety = new Quaternionf().rotateZ((float) java.lang.Math.toRadians(90.0));
        Quaternionf sampled = pose.jointPose(0).rotation();
        assertEquals(ninety.z, sampled.z, 1e-4f);
        assertEquals(ninety.w, sampled.w, 1e-4f);
    }

    @Test
    void cubicSplineWithZeroTangentsAveragesValuesAtMidpoint() {
        Skeleton skeleton = twoJointSkeleton();
        float[] values = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0};
        ClipChannel channel = new ClipChannel(0, ClipProperty.TRANSLATION, ClipInterpolation.CUBIC_SPLINE,
                new float[]{0.0f, 1.0f}, values);
        Clip clip = new Clip("cubic", 1.0f, skeleton.nameChecksum(), List.of(channel));

        SkeletonPose pose = new SkeletonPose(skeleton.jointCount());
        new ClipSampler().sample(clip, new BindPose(skeleton), 0.5f, pose);

        assertEquals(2.0f, pose.jointPose(0).translation().y, 1e-6f);
    }

    @Test
    void clampsBeforeFirstAndAfterLastKey() {
        Skeleton skeleton = twoJointSkeleton();
        ClipChannel channel = new ClipChannel(0, ClipProperty.TRANSLATION, ClipInterpolation.LINEAR,
                new float[]{1.0f, 2.0f}, new float[]{0, 3, 0, 0, 9, 0});
        Clip clip = new Clip("clamp", 3.0f, skeleton.nameChecksum(), List.of(channel));

        SkeletonPose before = new SkeletonPose(skeleton.jointCount());
        SkeletonPose after = new SkeletonPose(skeleton.jointCount());
        new ClipSampler().sample(clip, new BindPose(skeleton), 0.0f, before);
        new ClipSampler().sample(clip, new BindPose(skeleton), 5.0f, after);

        assertEquals(3.0f, before.jointPose(0).translation().y, 1e-6f);
        assertEquals(9.0f, after.jointPose(0).translation().y, 1e-6f);
    }

    @Test
    void checksumMismatchThrows() {
        Skeleton skeleton = twoJointSkeleton();
        Clip clip = new Clip("mismatch", 1.0f, skeleton.nameChecksum() + 1L,
                List.of(new ClipChannel(0, ClipProperty.TRANSLATION, ClipInterpolation.STEP,
                        new float[]{0.0f}, new float[]{0, 0, 0})));

        SkeletonPose pose = new SkeletonPose(skeleton.jointCount());
        assertThrows(EpysiaException.class, () -> new ClipSampler().sample(clip, new BindPose(skeleton), 0.0f, pose));
    }

    @Test
    void keepsAllocationFreeSteadyStateReusingPose() {
        Skeleton skeleton = twoJointSkeleton();
        ClipChannel channel = new ClipChannel(0, ClipProperty.TRANSLATION, ClipInterpolation.LINEAR,
                new float[]{0.0f, 1.0f}, new float[]{0, 0, 0, 0, 2, 0});
        Clip clip = new Clip("reuse", 1.0f, skeleton.nameChecksum(), List.of(channel));
        SkeletonPose pose = new SkeletonPose(skeleton.jointCount());
        ClipSampler sampler = new ClipSampler();
        Matrix4f[] matrices = allocateMatrices(skeleton.jointCount());

        sampler.sample(clip, new BindPose(skeleton), 0.5f, pose);
        pose.computeSkinningMatrices(skeleton, matrices);
        sampler.sample(clip, new BindPose(skeleton), 1.0f, pose);
        pose.computeSkinningMatrices(skeleton, matrices);

        Vector4f rowOne = new Vector4f();
        matrices[0].getRow(1, rowOne);
        assertTrue(rowOne.w > 1.999f && rowOne.w < 2.001f);
    }
}
