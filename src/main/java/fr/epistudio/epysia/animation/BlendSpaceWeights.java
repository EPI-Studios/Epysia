package fr.epistudio.epysia.animation;

import java.util.Arrays;
import java.util.List;

public final class BlendSpaceWeights {
    private static final int PLANE_NEIGHBOURS = 3;
    private static final float MINIMUM_DISTANCE = 1.0e-4f;

    private float[] weights = new float[0];

    public float[] compute(List<BlendSample> samples, BlendSpaceShape shape, float positionX, float positionY) {
        ensureCapacity(samples.size());
        if (samples.isEmpty()) {
            return weights;
        }
        if (shape == BlendSpaceShape.LINE) {
            computeLine(samples, positionX);
        } else {
            computePlane(samples, positionX, positionY);
        }
        return weights;
    }

    public static float weightedDuration(List<BlendSample> samples, float[] weights) {
        float duration = 0.0f;
        float total = 0.0f;
        for (int index = 0; index < samples.size(); index++) {
            Clip clip = samples.get(index).resolvedClip().orElse(null);
            if (clip == null || weights[index] <= 0.0f) {
                continue;
            }
            duration += weights[index] * clip.durationSeconds();
            total += weights[index];
        }
        return total <= 0.0f ? 0.0f : duration / total;
    }

    private void computeLine(List<BlendSample> samples, float position) {
        int left = -1;
        int right = -1;
        for (int index = 0; index < samples.size(); index++) {
            float candidate = samples.get(index).positionX();
            if (candidate <= position && (left < 0 || candidate > samples.get(left).positionX())) {
                left = index;
            }
            if (candidate >= position && (right < 0 || candidate < samples.get(right).positionX())) {
                right = index;
            }
        }
        assignBracket(samples, position, left, right);
    }

    private void assignBracket(List<BlendSample> samples, float position, int left, int right) {
        if (left < 0) {
            weights[right] = 1.0f;
            return;
        }
        if (right < 0 || left == right) {
            weights[left] = 1.0f;
            return;
        }
        float span = samples.get(right).positionX() - samples.get(left).positionX();
        float factor = span <= 0.0f ? 0.0f : (position - samples.get(left).positionX()) / span;
        weights[left] = 1.0f - factor;
        weights[right] = factor;
    }

    private void computePlane(List<BlendSample> samples, float positionX, float positionY) {
        int[] nearest = new int[Math.min(PLANE_NEIGHBOURS, samples.size())];
        float[] distances = new float[nearest.length];
        selectNearest(samples, positionX, positionY, nearest, distances);
        float total = 0.0f;
        for (int slot = 0; slot < nearest.length; slot++) {
            total += 1.0f / distances[slot];
        }
        for (int slot = 0; slot < nearest.length; slot++) {
            weights[nearest[slot]] = 1.0f / distances[slot] / total;
        }
    }

    private void selectNearest(List<BlendSample> samples, float positionX, float positionY,
                               int[] nearest, float[] distances) {
        Arrays.fill(distances, Float.POSITIVE_INFINITY);
        for (int index = 0; index < samples.size(); index++) {
            float distance = Math.max(MINIMUM_DISTANCE, distanceTo(samples.get(index), positionX, positionY));
            insertNearest(nearest, distances, index, distance);
        }
    }

    private static void insertNearest(int[] nearest, float[] distances, int index, float distance) {
        for (int slot = 0; slot < distances.length; slot++) {
            if (distance >= distances[slot]) {
                continue;
            }
            for (int shift = distances.length - 1; shift > slot; shift--) {
                distances[shift] = distances[shift - 1];
                nearest[shift] = nearest[shift - 1];
            }
            distances[slot] = distance;
            nearest[slot] = index;
            return;
        }
    }

    private static float distanceTo(BlendSample sample, float positionX, float positionY) {
        float deltaX = sample.positionX() - positionX;
        float deltaY = sample.positionY() - positionY;
        return (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    private void ensureCapacity(int count) {
        if (weights.length < count) {
            weights = new float[count];
            return;
        }
        Arrays.fill(weights, 0, count, 0.0f);
    }
}
