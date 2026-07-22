package fr.epistudio.epysia.vfx;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleEffectEmissionTest {

    @Test
    void teleportWhilePausedDoesNotSpikeDistanceEmissionOnResume() {
        ParticleEffect effect = new ParticleEffect()
                .setEmissionRate(0.0f)
                .setDistanceRate(10.0f);

        effect.advanceEmission(0.016f, new Vector3f());
        effect.setPlaying(false);
        effect.advanceEmission(0.016f, new Vector3f(100.0f, 0.0f, 0.0f));
        effect.setPlaying(true);
        int resumed = effect.advanceEmission(0.016f, new Vector3f(100.0f, 0.0f, 0.0f));

        assertEquals(0, resumed);
        assertEquals(0.0f, effect.frameMotion().x());
    }

    @Test
    void burstAtExactlyTheDurationFiresOnANonLoopingEffect() {
        ParticleEffect effect = new ParticleEffect()
                .setEmissionRate(0.0f)
                .setDistanceRate(0.0f)
                .setDuration(1.0f)
                .setLooping(false)
                .setBursts(List.of(
                        ParticleBurst.at(0.0f, 5),
                        ParticleBurst.at(0.5f, 7),
                        ParticleBurst.at(1.0f, 9)));

        Vector3f origin = new Vector3f();
        int spawned = 0;
        for (int step = 0; step < 20; step++) {
            spawned += effect.advanceEmission(0.1f, origin);
        }

        assertEquals(21, spawned);
    }

    @Test
    void prewarmEnabledAfterTheFirstFrameStillRuns() {
        ParticleEffect effect = new ParticleEffect().setDuration(1.0f);

        assertEquals(0, effect.consumePrewarmSteps());
        effect.setPrewarm(true);

        assertTrue(effect.consumePrewarmSteps() > 0);
        assertEquals(0, effect.consumePrewarmSteps());
    }
}
