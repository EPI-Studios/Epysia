package fr.epistudio.epysia.vfx;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

@EpysiaComponent(name = "Particle Effect", category = "Effects")
public final class ParticleEffect extends Component {

    public static final int MINIMUM_POOL_SIZE = 64;
    public static final int MAXIMUM_POOL_SIZE = 65536;
    public static final float MINIMUM_DURATION_SECONDS = 0.05f;

    private static final float PREWARM_STEP_SECONDS = 1.0f / 30.0f;
    private static final int MAXIMUM_PREWARM_STEPS = 240;
    private static final int PREWARM_SETTLE_FRAMES = 4;

    public enum SimulationSpace {
        LOCAL,
        WORLD
    }

    @Export(label = "Graph")
    private String graphPath = "";
    @Export(label = "Pool Size", min = MINIMUM_POOL_SIZE, max = MAXIMUM_POOL_SIZE, step = 64.0f)
    private int poolSize = 1024;
    @Export(label = "Emission Rate", min = 0.0f, max = 10000.0f, step = 10.0f)
    private float emissionRate = 100.0f;
    @Export(label = "Distance Rate", min = 0.0f, max = 1000.0f, step = 1.0f)
    private float distanceRate;
    @Export(label = "Bursts")
    private String bursts = "";
    @Export(label = "Duration", min = MINIMUM_DURATION_SECONDS, max = 600.0f, step = 0.25f)
    private float duration = 5.0f;
    @Export(label = "Looping")
    private boolean looping = true;
    @Export(label = "Prewarm")
    private boolean prewarm;
    @Export(label = "Simulation Space")
    private SimulationSpace simulationSpace = SimulationSpace.LOCAL;
    @Export(label = "Seed", min = 1.0f, max = 1000000.0f, step = 1.0f)
    private int seed = 1;
    @Export(label = "Playing")
    private boolean playing = true;

    private final ParticleEmission emission = new ParticleEmission();
    private List<ParticleBurst> decodedBursts = List.of();
    private String decodedBurstsSource = "";
    private boolean prewarmDone;
    private int settleFrames;
    private long totalSpawned;

    public String graphPath() {
        return graphPath;
    }

    public ParticleEffect setGraphPath(String value) {
        graphPath = value;
        return this;
    }

    public int poolSize() {
        return Math.clamp(poolSize, MINIMUM_POOL_SIZE, MAXIMUM_POOL_SIZE);
    }

    public float emissionRate() {
        return emissionRate;
    }

    public float distanceRate() {
        return distanceRate;
    }

    public float durationSeconds() {
        return Math.max(MINIMUM_DURATION_SECONDS, duration);
    }

    public boolean isLooping() {
        return looping;
    }

    public boolean isPrewarm() {
        return prewarm;
    }

    public SimulationSpace simulationSpace() {
        return simulationSpace;
    }

    public int seed() {
        return seed;
    }

    public boolean isPlaying() {
        return playing;
    }

    public ParticleEffect setPoolSize(int value) {
        poolSize = value;
        return this;
    }

    public ParticleEffect setEmissionRate(float value) {
        emissionRate = value;
        return this;
    }

    public ParticleEffect setDistanceRate(float value) {
        distanceRate = value;
        return this;
    }

    public ParticleEffect setDuration(float seconds) {
        duration = seconds;
        return this;
    }

    public ParticleEffect setLooping(boolean value) {
        looping = value;
        return this;
    }

    public ParticleEffect setPrewarm(boolean value) {
        prewarm = value;
        return this;
    }

    public ParticleEffect setSimulationSpace(SimulationSpace space) {
        simulationSpace = space;
        return this;
    }

    public ParticleEffect setSeed(int value) {
        seed = value;
        return this;
    }

    public ParticleEffect setPlaying(boolean value) {
        playing = value;
        return this;
    }

    public List<ParticleBurst> bursts() {
        if (!decodedBurstsSource.equals(bursts)) {
            decodedBursts = ParticleBurst.decode(bursts);
            decodedBurstsSource = bursts;
        }
        return decodedBursts;
    }

    public ParticleEffect setBursts(List<ParticleBurst> value) {
        bursts = ParticleBurst.encode(value);
        decodedBursts = List.copyOf(value);
        decodedBurstsSource = bursts;
        return this;
    }

    public ParticleEffect addBurst(ParticleBurst burst) {
        List<ParticleBurst> merged = new ArrayList<>(bursts());
        merged.add(burst);
        return setBursts(merged);
    }

    public List<ParticleBurst> burstsExceedingDuration() {
        float duration = durationSeconds();
        return bursts().stream().filter(burst -> !burst.fitsWithin(duration)).toList();
    }

    public int advanceEmission(float deltaSeconds, Vector3fc emitterWorldPosition) {
        if (!playing) {
            emission.suspend();
            return 0;
        }
        return emission.advance(emissionSettings(), deltaSeconds, emitterWorldPosition);
    }

    private EmissionSettings emissionSettings() {
        return new EmissionSettings(emissionRate, distanceRate, durationSeconds(), looping, bursts());
    }

    public float normalizedTime() {
        return emission.normalizedTime(durationSeconds());
    }

    public float elapsedSeconds() {
        return emission.elapsedSeconds();
    }

    public float distanceTravelled() {
        return emission.distanceTravelled();
    }

    public Vector3fc frameMotion() {
        return emission.frameMotion();
    }

    public float simulationSpaceFollow() {
        return simulationSpace == SimulationSpace.LOCAL ? 1.0f : 0.0f;
    }

    public int consumePrewarmSteps() {
        if (!prewarm || prewarmDone || !playing) {
            return 0;
        }
        prewarmDone = true;
        settleFrames = PREWARM_SETTLE_FRAMES;
        return Math.min(MAXIMUM_PREWARM_STEPS, (int) Math.ceil(durationSeconds() / PREWARM_STEP_SECONDS));
    }

    public float prewarmStepSeconds() {
        return PREWARM_STEP_SECONDS;
    }

    public float settledDeltaSeconds(float deltaSeconds) {
        if (settleFrames <= 0) {
            return deltaSeconds;
        }
        settleFrames--;
        return Math.min(deltaSeconds, PREWARM_STEP_SECONDS);
    }

    public void restart() {
        emission.restart();
        prewarmDone = false;
        settleFrames = 0;
        totalSpawned = 0L;
    }

    public long totalSpawned() {
        return totalSpawned;
    }

    public void recordSpawned(int count) {
        totalSpawned += count;
    }
}
