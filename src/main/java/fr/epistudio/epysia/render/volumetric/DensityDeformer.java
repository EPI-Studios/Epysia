package fr.epistudio.epysia.render.volumetric;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

@EpysiaComponent(name = "Density Deformer", category = "Rendering",
        description = "Carves a volume's density with moving shapes, for smoke pushed aside.")
@RequiresComponent(DensityVolume.class)
public final class DensityDeformer extends Component {
    public static final int HARD_LIMIT = 256;
    public static final int FLOATS_PER_ENTRY = 8;

    @Export(label = "Max Active", min = 1.0f, max = 256.0f, step = 1.0f)
    private int maxActive = 32;

    @Export(label = "Depth", min = 0.0f, max = 60.0f, step = 0.1f)
    private float depth = 15.0f;

    @Export(label = "Start Radius", min = -2.0f, max = 2.0f, step = 0.01f)
    private float startRadius;

    @Export(label = "End Radius", min = -2.0f, max = 2.0f, step = 0.01f)
    private float endRadius;

    private final List<DeformerEntry> entries = new ArrayList<>();
    private final float[] packed = new float[HARD_LIMIT * FLOATS_PER_ENTRY];
    private int packedCount;

    public DensityDeformer punch(Vector3fc origin, Vector3fc direction, float closeSpeed) {
        if (entries.size() >= Math.min(maxActive, HARD_LIMIT)) {
            entries.removeFirst();
        }
        entries.add(new DeformerEntry(new Vector3f(origin), new Vector3f(direction).normalize(),
                Math.max(0.05f, closeSpeed)));
        return this;
    }

    public void advance(float deltaTimeSeconds) {
        entries.removeIf(entry -> entry.advance(deltaTimeSeconds));
        packEntries();
    }

    private void packEntries() {
        packedCount = Math.min(entries.size(), Math.min(maxActive, HARD_LIMIT));
        for (int index = 0; index < packedCount; index++) {
            entries.get(index).writeInto(packed, index * FLOATS_PER_ENTRY, startRadius, endRadius);
        }
    }

    static float ease(float progress) {
        if (progress < 0.25f) {
            return 1.0f - (float) Math.pow(1.0f - 2.0f * progress, 15.0);
        }
        float tail = 1.25f * (progress - 0.25f);
        return 1.0f - tail * tail;
    }

    public float[] packedEntries() {
        return packed;
    }

    public int activeCount() {
        return packedCount;
    }

    public float depth() {
        return depth;
    }

    public DensityDeformer setDepth(float value) {
        depth = Math.max(0.0f, value);
        return this;
    }

    public DensityDeformer setRadii(float start, float end) {
        startRadius = start;
        endRadius = end;
        return this;
    }

    public void clear() {
        entries.clear();
        packedCount = 0;
    }

    private static final class DeformerEntry {
        private final Vector3f origin;
        private final Vector3f direction;
        private final float closeSpeed;
        private float progress;

        private DeformerEntry(Vector3f origin, Vector3f direction, float closeSpeed) {
            this.origin = origin;
            this.direction = direction;
            this.closeSpeed = closeSpeed;
        }

        private boolean advance(float deltaTimeSeconds) {
            progress += deltaTimeSeconds * closeSpeed;
            return progress > 1.0f;
        }

        private void writeInto(float[] target, int offset, float startRadius, float endRadius) {
            float eased = ease(progress);
            target[offset] = origin.x;
            target[offset + 1] = origin.y;
            target[offset + 2] = origin.z;
            target[offset + 3] = lerpRadius(startRadius, eased);
            target[offset + 4] = direction.x;
            target[offset + 5] = direction.y;
            target[offset + 6] = direction.z;
            target[offset + 7] = lerpRadius(endRadius, eased);
        }

        private static float lerpRadius(float target, float eased) {
            return -2.0f + (target + 2.0f) * eased;
        }
    }
}
