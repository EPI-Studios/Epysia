package fr.epistudio.epysia.debug;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DebugDraw {

    public static final float ONE_FRAME = 0.0f;

    private static final DebugDraw DETACHED = new DebugDraw().setEnabled(false);
    private static final int INITIAL_SEGMENTS = 512;
    private static final int FLOATS_PER_SEGMENT = 6;

    private float[] endpoints = new float[INITIAL_SEGMENTS * FLOATS_PER_SEGMENT];
    private int[] colors = new int[INITIAL_SEGMENTS];
    private float[] remainingSeconds = new float[INITIAL_SEGMENTS];
    private int segmentCount;
    private final List<DebugLabel> labels = new ArrayList<>();
    private boolean enabled = true;

    public static DebugDraw detached() {
        return DETACHED;
    }

    public boolean enabled() {
        return enabled;
    }

    public DebugDraw setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            clear();
        }
        return this;
    }

    public void line(float startX, float startY, float startZ,
                     float endX, float endY, float endZ, int color, float seconds) {
        if (!enabled) {
            return;
        }
        growIfFull();
        int base = segmentCount * FLOATS_PER_SEGMENT;
        endpoints[base] = startX;
        endpoints[base + 1] = startY;
        endpoints[base + 2] = startZ;
        endpoints[base + 3] = endX;
        endpoints[base + 4] = endY;
        endpoints[base + 5] = endZ;
        colors[segmentCount] = color;
        remainingSeconds[segmentCount] = Math.max(0.0f, seconds);
        segmentCount++;
    }

    public void line(Vector3fc from, Vector3fc to, int color) {
        line(from, to, color, ONE_FRAME);
    }

    public void line(Vector3fc from, Vector3fc to, int color, float seconds) {
        line(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), color, seconds);
    }

    public void ray(Vector3fc origin, Vector3fc direction, int color) {
        ray(origin, direction, color, ONE_FRAME);
    }

    public void ray(Vector3fc origin, Vector3fc direction, int color, float seconds) {
        DebugShapes.arrow(this, origin, direction, color, seconds);
    }

    public void cross(Vector3fc center, float size, int color) {
        cross(center, size, color, ONE_FRAME);
    }

    public void cross(Vector3fc center, float size, int color, float seconds) {
        DebugShapes.cross(this, center, size, color, seconds);
    }

    public void box(Vector3fc center, Vector3fc halfExtents, int color) {
        box(center, halfExtents, color, ONE_FRAME);
    }

    public void box(Vector3fc center, Vector3fc halfExtents, int color, float seconds) {
        DebugShapes.axisAlignedBox(this, center, halfExtents, color, seconds);
    }

    public void box(Matrix4fc transform, Vector3fc halfExtents, int color, float seconds) {
        DebugShapes.orientedBox(this, transform, halfExtents, color, seconds);
    }

    public void sphere(Vector3fc center, float radius, int color) {
        sphere(center, radius, color, ONE_FRAME);
    }

    public void sphere(Vector3fc center, float radius, int color, float seconds) {
        DebugShapes.sphere(this, center, radius, color, seconds);
    }

    public void capsule(Vector3fc start, Vector3fc end, float radius, int color, float seconds) {
        DebugShapes.capsule(this, start, end, radius, color, seconds);
    }

    public void text(Vector3fc position, String content, int color) {
        text(position, content, color, ONE_FRAME);
    }

    public void text(Vector3fc position, String content, int color, float seconds) {
        if (!enabled || content == null || content.isEmpty()) {
            return;
        }
        labels.add(new DebugLabel(position.x(), position.y(), position.z(),
                content, color, Math.max(0.0f, seconds)));
    }

    public void advance(float deltaSeconds) {
        int kept = 0;
        for (int index = 0; index < segmentCount; index++) {
            float remaining = remainingSeconds[index] - deltaSeconds;
            if (remaining <= 0.0f) {
                continue;
            }
            copySegment(index, kept);
            remainingSeconds[kept] = remaining;
            kept++;
        }
        segmentCount = kept;
        labels.removeIf(label -> !label.advance(deltaSeconds));
    }

    private void copySegment(int source, int destination) {
        if (source != destination) {
            System.arraycopy(endpoints, source * FLOATS_PER_SEGMENT,
                    endpoints, destination * FLOATS_PER_SEGMENT, FLOATS_PER_SEGMENT);
            colors[destination] = colors[source];
        }
    }

    public void clear() {
        segmentCount = 0;
        labels.clear();
    }

    public int segmentCount() {
        return segmentCount;
    }

    public float[] endpoints() {
        return endpoints;
    }

    public int[] colors() {
        return colors;
    }

    public List<DebugLabel> labels() {
        return labels;
    }

    private void growIfFull() {
        if (segmentCount < colors.length) {
            return;
        }
        int grown = colors.length * 2;
        endpoints = Arrays.copyOf(endpoints, grown * FLOATS_PER_SEGMENT);
        colors = Arrays.copyOf(colors, grown);
        remainingSeconds = Arrays.copyOf(remainingSeconds, grown);
    }
}
