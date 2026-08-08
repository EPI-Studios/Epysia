package fr.epistudio.epysia.animation;

import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlendSpaceSamplerTest {
    private static final float TOLERANCE = 1.0e-4f;

    private final BlendSpaceWeights weights = new BlendSpaceWeights();
    private final BlendSpaceSampler sampler = new BlendSpaceSampler(new ClipSampler());

    @Test
    void theMidPointOfALineIsTheAverageOfBothClips() {
        Skeleton skeleton = singleJointSkeleton();
        List<BlendSample> samples = List.of(
                sampleAt(0.0f, translationClip(skeleton, 0.0f)),
                sampleAt(1.0f, translationClip(skeleton, 10.0f)));
        SkeletonPose pose = sample(samples, BlendSpaceShape.LINE, 0.5f, 0.0f, skeleton);
        assertEquals(5.0f, pose.jointPose(0).translation().x, TOLERANCE);
    }

    @Test
    void anEndOfTheLineTakesItsClipAlone() {
        Skeleton skeleton = singleJointSkeleton();
        List<BlendSample> samples = List.of(
                sampleAt(0.0f, translationClip(skeleton, 0.0f)),
                sampleAt(1.0f, translationClip(skeleton, 10.0f)));
        SkeletonPose pose = sample(samples, BlendSpaceShape.LINE, 1.0f, 0.0f, skeleton);
        assertEquals(10.0f, pose.jointPose(0).translation().x, TOLERANCE);
    }

    @Test
    void aPositionBeyondTheLineClampsToTheNearestClip() {
        Skeleton skeleton = singleJointSkeleton();
        List<BlendSample> samples = List.of(
                sampleAt(0.0f, translationClip(skeleton, 0.0f)),
                sampleAt(1.0f, translationClip(skeleton, 10.0f)));
        SkeletonPose pose = sample(samples, BlendSpaceShape.LINE, 4.0f, 0.0f, skeleton);
        assertEquals(10.0f, pose.jointPose(0).translation().x, TOLERANCE);
    }

    @Test
    void planeWeightsSumToOne() {
        List<BlendSample> samples = List.of(
                planeSample(0.0f, 0.0f), planeSample(1.0f, 0.0f),
                planeSample(0.0f, 1.0f), planeSample(1.0f, 1.0f));
        float[] computed = weights.compute(samples, BlendSpaceShape.PLANE, 0.4f, 0.6f);
        float total = 0.0f;
        for (float weight : computed) {
            assertTrue(weight >= 0.0f, "a blend weight went negative");
            total += weight;
        }
        assertEquals(1.0f, total, TOLERANCE);
    }

    @Test
    void anEmptySpaceContributesNothing() {
        Skeleton skeleton = singleJointSkeleton();
        SkeletonPose pose = new SkeletonPose(1);
        assertTrue(!sampler.sample(List.of(), new float[0], new BindPose(skeleton), 0.0f,
                pose, new SkeletonPose(1)));
    }

    private SkeletonPose sample(List<BlendSample> samples, BlendSpaceShape shape, float positionX,
                                float positionY, Skeleton skeleton) {
        SkeletonPose pose = new SkeletonPose(skeleton.jointCount());
        SkeletonPose scratch = new SkeletonPose(skeleton.jointCount());
        float[] computed = weights.compute(samples, shape, positionX, positionY);
        assertTrue(sampler.sample(samples, computed, new BindPose(skeleton), 0.0f, pose, scratch),
                "the blend space produced no pose");
        return pose;
    }

    private static BlendSample sampleAt(float positionX, Clip clip) {
        return new BlendSample().setPositionX(positionX).assignClip("memory:clip", clip);
    }

    private static BlendSample planeSample(float positionX, float positionY) {
        return new BlendSample().setPositionX(positionX).setPositionY(positionY);
    }

    private static Skeleton singleJointSkeleton() {
        float[] identity = new float[16];
        new Matrix4f().get(identity);
        return new Skeleton(List.of(new Joint("root", -1, identity, identity.clone())));
    }

    private static Clip translationClip(Skeleton skeleton, float offsetX) {
        return new Clip("translate", 1.0f, skeleton.nameChecksum(), List.of(
                new ClipChannel(0, ClipProperty.TRANSLATION, ClipInterpolation.LINEAR,
                        new float[]{0.0f, 1.0f},
                        new float[]{offsetX, 0.0f, 0.0f, offsetX, 0.0f, 0.0f})));
    }
}
