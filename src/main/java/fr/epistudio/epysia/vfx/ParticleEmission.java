package fr.epistudio.epysia.vfx;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

final class ParticleEmission {

    private final Vector3f lastEmitterPosition = new Vector3f();
    private final Vector3f frameMotion = new Vector3f();
    private boolean hasLastEmitterPosition;
    private boolean started;
    private float spawnAccumulator;
    private float distanceAccumulator;
    private float cycleSeconds;
    private float elapsedSeconds;
    private float distanceTravelled;

    int advance(EmissionSettings settings, float deltaSeconds, Vector3fc emitterPosition) {
        trackMotion(emitterPosition);
        float previousCycleSeconds = started ? cycleSeconds : -1.0f;
        started = true;
        boolean wrapped = advanceClock(settings, deltaSeconds);
        if (isFinished(settings)) {
            return 0;
        }
        return rateEmission(settings.emissionRate() * deltaSeconds)
                + distanceEmission(settings.distanceRate())
                + burstEmission(settings, previousCycleSeconds, wrapped);
    }

    private void trackMotion(Vector3fc emitterPosition) {
        if (!hasLastEmitterPosition) {
            lastEmitterPosition.set(emitterPosition);
            hasLastEmitterPosition = true;
        }
        emitterPosition.sub(lastEmitterPosition, frameMotion);
        lastEmitterPosition.set(emitterPosition);
        distanceTravelled += frameMotion.length();
    }

    private boolean advanceClock(EmissionSettings settings, float deltaSeconds) {
        elapsedSeconds += deltaSeconds;
        cycleSeconds += deltaSeconds;
        float duration = settings.durationSeconds();
        if (cycleSeconds < duration) {
            return false;
        }
        if (!settings.looping()) {
            cycleSeconds = duration;
            return false;
        }
        cycleSeconds -= duration * (float) Math.floor(cycleSeconds / duration);
        return true;
    }

    private boolean isFinished(EmissionSettings settings) {
        return !settings.looping() && cycleSeconds >= settings.durationSeconds();
    }

    private int rateEmission(float particles) {
        spawnAccumulator += Math.max(0.0f, particles);
        int count = (int) spawnAccumulator;
        spawnAccumulator -= count;
        return count;
    }

    private int distanceEmission(float particlesPerUnit) {
        if (particlesPerUnit <= 0.0f) {
            distanceAccumulator = 0.0f;
            return 0;
        }
        distanceAccumulator += frameMotion.length() * particlesPerUnit;
        int count = (int) distanceAccumulator;
        distanceAccumulator -= count;
        return count;
    }

    private int burstEmission(EmissionSettings settings, float previousCycleSeconds, boolean wrapped) {
        if (!wrapped) {
            return firings(settings.bursts(), previousCycleSeconds, cycleSeconds);
        }
        return firings(settings.bursts(), previousCycleSeconds, settings.durationSeconds())
                + firings(settings.bursts(), -1.0f, cycleSeconds);
    }

    private static int firings(List<ParticleBurst> bursts, float fromSeconds, float toSeconds) {
        int count = 0;
        for (ParticleBurst burst : bursts) {
            count += burst.firingsBetween(fromSeconds, toSeconds);
        }
        return count;
    }

    float normalizedTime(float durationSeconds) {
        if (durationSeconds <= 0.0f) {
            return 0.0f;
        }
        return Math.clamp(cycleSeconds / durationSeconds, 0.0f, 1.0f);
    }

    float cycleSeconds() {
        return cycleSeconds;
    }

    float elapsedSeconds() {
        return elapsedSeconds;
    }

    float distanceTravelled() {
        return distanceTravelled;
    }

    Vector3fc frameMotion() {
        return frameMotion;
    }

    void restart() {
        started = false;
        cycleSeconds = 0.0f;
        elapsedSeconds = 0.0f;
        spawnAccumulator = 0.0f;
        distanceAccumulator = 0.0f;
    }
}
