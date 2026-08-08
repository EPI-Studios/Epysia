package fr.epistudio.epysia.render.volumetric;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.RequiresComponent;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@EpysiaComponent(name = "Density Propagation", category = "Rendering")
@RequiresComponent(DensityVolume.class)
public final class DensityPropagation extends Component implements DensitySource {
    @Export(label = "Max Radius", min = 0.1f, max = 100.0f, step = 0.1f)
    private final Vector3f maxRadius = new Vector3f(5.0f, 4.0f, 5.0f);

    @Export(label = "Growth Speed", min = 0.01f, max = 5.0f, step = 0.01f)
    private float growthSpeed = 2.0f;

    @Export(label = "Propagation Distance", min = 0.0f, max = 128.0f, step = 1.0f)
    private int propagationDistance = 16;

    @Export(label = "Seed On Start")
    private boolean seedOnStart;

    private final Vector3f seedPoint = new Vector3f();
    private final Vector3f growthRadius = new Vector3f();
    private float growth;
    private boolean seedRequested;
    private boolean growing;

    @Override
    public void advance(float deltaTimeSeconds) {
        if (!growing) {
            return;
        }
        growth += growthSpeed * deltaTimeSeconds;
        float eased = ease(growth);
        growthRadius.set(maxRadius).mul(eased);
    }

    static float ease(float progress) {
        float eased = progress < 0.5f
                ? 2.0f * progress * progress
                : 1.0f - (1.0f / (5.0f * (2.0f * progress - 0.8f) + 1.0f));
        return Math.min(1.0f, eased);
    }

    public DensityPropagation seed(Vector3fc worldPoint) {
        seedPoint.set(worldPoint);
        growth = 0.0f;
        growthRadius.zero();
        seedRequested = true;
        growing = true;
        return this;
    }

    public DensityPropagation reset() {
        growth = 0.0f;
        growthRadius.zero();
        seedRequested = false;
        growing = false;
        return this;
    }

    public boolean seedOnStart() {
        return seedOnStart;
    }

    public DensityPropagation setSeedOnStart(boolean enabled) {
        seedOnStart = enabled;
        return this;
    }

    public boolean awaitingAutomaticSeed() {
        return seedOnStart && !growing;
    }

    public float growthProgress() {
        return growth;
    }

    public Vector3fc maxRadius() {
        return maxRadius;
    }

    public DensityPropagation setMaxRadius(float x, float y, float z) {
        maxRadius.set(x, y, z);
        return this;
    }

    public DensityPropagation setGrowthSpeed(float speed) {
        growthSpeed = Math.max(0.01f, speed);
        return this;
    }

    public DensityPropagation setPropagationDistance(int steps) {
        propagationDistance = Math.clamp(steps, 0, 128);
        return this;
    }

    @Override
    public boolean consumeSeedRequest() {
        boolean requested = seedRequested;
        seedRequested = false;
        return requested;
    }

    @Override
    public Vector3fc seedPoint() {
        return seedPoint;
    }

    @Override
    public Vector3fc growthRadius() {
        return growthRadius;
    }

    @Override
    public int propagationDistance() {
        return propagationDistance;
    }

    @Override
    public boolean growing() {
        return growing;
    }
}
