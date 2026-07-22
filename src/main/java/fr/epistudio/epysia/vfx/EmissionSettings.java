package fr.epistudio.epysia.vfx;

import java.util.List;

record EmissionSettings(float emissionRate, float distanceRate, float durationSeconds,
                        boolean looping, List<ParticleBurst> bursts) {
}
