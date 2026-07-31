package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.exceptions.EpysiaException;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class ClipSampler {

    private final Matrix4f bindScratch = new Matrix4f();
    private final Quaternionf rotationLeft = new Quaternionf();
    private final Quaternionf rotationRight = new Quaternionf();

    public void sample(Clip clip, Skeleton skeleton, float timeSeconds, SkeletonPose out) {
        if (clip.skeletonChecksum() != skeleton.nameChecksum()) {
            throw new EpysiaException("Clip skeletonChecksum " + clip.skeletonChecksum()
                    + " does not match skeleton nameChecksum " + skeleton.nameChecksum() + ".");
        }
        List<Joint> joints = skeleton.joints();
        for (int index = 0; index < joints.size(); index++) {
            applyBindDefaults(joints.get(index), out.jointPose(index));
        }
        List<ClipChannel> channels = clip.channels();
        for (int index = 0; index < channels.size(); index++) {
            ClipChannel channel = channels.get(index);
            applyChannel(channel, timeSeconds, out.jointPose(channel.jointIndex()));
        }
    }

    private void applyBindDefaults(Joint joint, JointPose pose) {
        bindScratch.set(joint.localBindTransform());
        pose.setFromMatrix(bindScratch);
    }

    private void applyChannel(ClipChannel channel, float timeSeconds, JointPose pose) {
        float[] times = channel.times();
        float clamped = clamp(timeSeconds, times[0], times[times.length - 1]);
        int leftKey = findLeftKey(times, clamped);
        switch (channel.property()) {
            case TRANSLATION -> evaluateVector(channel, leftKey, clamped, pose.translation());
            case SCALE -> evaluateVector(channel, leftKey, clamped, pose.scale());
            case ROTATION -> evaluateRotation(channel, leftKey, clamped, pose.rotation());
        }
    }

    private void evaluateVector(ClipChannel channel, int leftKey, float time, Vector3f out) {
        switch (channel.interpolation()) {
            case STEP -> readVector(channel.values(), valueOffset(channel, leftKey), out);
            case LINEAR -> lerpVector(channel, leftKey, time, out);
            case CUBIC_SPLINE -> hermiteVector(channel, leftKey, time, out);
        }
    }

    private void lerpVector(ClipChannel channel, int leftKey, float time, Vector3f out) {
        float[] times = channel.times();
        float[] values = channel.values();
        int rightKey = java.lang.Math.min(leftKey + 1, times.length - 1);
        float factor = interpolationFactor(times, leftKey, rightKey, time);
        int leftOffset = valueOffset(channel, leftKey);
        int rightOffset = valueOffset(channel, rightKey);
        for (int component = 0; component < 3; component++) {
            float start = values[leftOffset + component];
            out.setComponent(component, start + factor * (values[rightOffset + component] - start));
        }
    }

    private void hermiteVector(ClipChannel channel, int leftKey, float time, Vector3f out) {
        float[] times = channel.times();
        int rightKey = java.lang.Math.min(leftKey + 1, times.length - 1);
        if (rightKey == leftKey) {
            readVector(channel.values(), valueOffset(channel, leftKey), out);
            return;
        }
        float delta = times[rightKey] - times[leftKey];
        float factor = (time - times[leftKey]) / delta;
        for (int component = 0; component < 3; component++) {
            out.setComponent(component, hermiteComponent(channel, leftKey, rightKey, component, factor, delta));
        }
    }

    private void evaluateRotation(ClipChannel channel, int leftKey, float time, Quaternionf out) {
        switch (channel.interpolation()) {
            case STEP -> readRotation(channel.values(), valueOffset(channel, leftKey), out).normalize();
            case LINEAR -> lerpRotation(channel, leftKey, time, out);
            case CUBIC_SPLINE -> hermiteRotation(channel, leftKey, time, out);
        }
    }

    private void lerpRotation(ClipChannel channel, int leftKey, float time, Quaternionf out) {
        float[] times = channel.times();
        int rightKey = java.lang.Math.min(leftKey + 1, times.length - 1);
        readRotation(channel.values(), valueOffset(channel, leftKey), rotationLeft);
        readRotation(channel.values(), valueOffset(channel, rightKey), rotationRight);
        if (rotationLeft.dot(rotationRight) < 0.0f) {
            rotationRight.set(-rotationRight.x, -rotationRight.y, -rotationRight.z, -rotationRight.w);
        }
        rotationLeft.nlerp(rotationRight, interpolationFactor(times, leftKey, rightKey, time), out);
        out.normalize();
    }

    private void hermiteRotation(ClipChannel channel, int leftKey, float time, Quaternionf out) {
        float[] times = channel.times();
        int rightKey = java.lang.Math.min(leftKey + 1, times.length - 1);
        if (rightKey == leftKey) {
            readRotation(channel.values(), valueOffset(channel, leftKey), out).normalize();
            return;
        }
        float delta = times[rightKey] - times[leftKey];
        float factor = (time - times[leftKey]) / delta;
        out.set(hermiteComponent(channel, leftKey, rightKey, 0, factor, delta),
                hermiteComponent(channel, leftKey, rightKey, 1, factor, delta),
                hermiteComponent(channel, leftKey, rightKey, 2, factor, delta),
                hermiteComponent(channel, leftKey, rightKey, 3, factor, delta)).normalize();
    }

    private static float hermiteComponent(ClipChannel channel, int leftKey, int rightKey,
                                          int component, float factor, float delta) {
        int components = channel.property().componentCount();
        float[] values = channel.values();
        int leftBase = leftKey * components * 3;
        int rightBase = rightKey * components * 3;
        float startValue = values[leftBase + components + component];
        float outTangent = values[leftBase + 2 * components + component];
        float endValue = values[rightBase + components + component];
        float inTangent = values[rightBase + component];
        return hermite(startValue, outTangent, endValue, inTangent, factor, delta);
    }

    private static float hermite(float startValue, float outTangent, float endValue,
                                 float inTangent, float t, float delta) {
        float squared = t * t;
        float cubed = squared * t;
        float startBasis = 2.0f * cubed - 3.0f * squared + 1.0f;
        float outBasis = cubed - 2.0f * squared + t;
        float endBasis = -2.0f * cubed + 3.0f * squared;
        float inBasis = cubed - squared;
        return startBasis * startValue + outBasis * delta * outTangent
                + endBasis * endValue + inBasis * delta * inTangent;
    }

    private static void readVector(float[] values, int offset, Vector3f out) {
        out.set(values[offset], values[offset + 1], values[offset + 2]);
    }

    private static Quaternionf readRotation(float[] values, int offset, Quaternionf out) {
        return out.set(values[offset], values[offset + 1], values[offset + 2], values[offset + 3]);
    }

    private static int valueOffset(ClipChannel channel, int key) {
        int components = channel.property().componentCount();
        if (channel.interpolation() == ClipInterpolation.CUBIC_SPLINE) {
            return key * components * 3 + components;
        }
        return key * components;
    }

    private static float interpolationFactor(float[] times, int leftKey, int rightKey, float time) {
        if (rightKey == leftKey) {
            return 0.0f;
        }
        return (time - times[leftKey]) / (times[rightKey] - times[leftKey]);
    }

    private static int findLeftKey(float[] times, float time) {
        int left = 0;
        int right = times.length - 1;
        while (left < right) {
            int middle = (left + right + 1) >>> 1;
            if (times[middle] <= time) {
                left = middle;
            } else {
                right = middle - 1;
            }
        }
        return left;
    }

    private static float clamp(float value, float low, float high) {
        return java.lang.Math.max(low, java.lang.Math.min(high, value));
    }
}
