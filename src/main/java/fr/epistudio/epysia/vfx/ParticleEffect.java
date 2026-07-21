package fr.epistudio.epysia.vfx;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.EpysiaComponent;
import fr.epistudio.epysia.components.Export;

@EpysiaComponent(name = "Particle Effect", category = "Effects")
public final class ParticleEffect extends Component {

    public static final int MINIMUM_POOL_SIZE = 64;
    public static final int MAXIMUM_POOL_SIZE = 65536;

    @Export(label = "Pool Size", min = MINIMUM_POOL_SIZE, max = MAXIMUM_POOL_SIZE, step = 64.0f)
    private int poolSize = 1024;
    @Export(label = "Emission Rate", min = 0.0f, max = 10000.0f, step = 10.0f)
    private float emissionRate = 100.0f;
    @Export(label = "Seed", min = 1.0f, max = 1000000.0f, step = 1.0f)
    private int seed = 1;
    @Export(label = "Playing")
    private boolean playing = true;

    private float spawnAccumulator;
    private long totalSpawned;

    public int poolSize() {
        return Math.clamp(poolSize, MINIMUM_POOL_SIZE, MAXIMUM_POOL_SIZE);
    }

    public float emissionRate() {
        return emissionRate;
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

    public ParticleEffect setSeed(int value) {
        seed = value;
        return this;
    }

    public ParticleEffect setPlaying(boolean value) {
        playing = value;
        return this;
    }

    public int consumeSpawnCount(float deltaSeconds) {
        if (!playing) {
            return 0;
        }
        spawnAccumulator += emissionRate * deltaSeconds;
        int spawned = (int) spawnAccumulator;
        spawnAccumulator -= spawned;
        return spawned;
    }

    public long totalSpawned() {
        return totalSpawned;
    }

    public void recordSpawned(int count) {
        totalSpawned += count;
    }
}
