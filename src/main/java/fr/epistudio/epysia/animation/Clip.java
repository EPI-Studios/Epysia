package fr.epistudio.epysia.animation;

import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.List;

public record Clip(String name, float durationSeconds, long skeletonChecksum, List<ClipChannel> channels) {

    public Clip {
        if (durationSeconds <= 0.0f) {
            throw new EpysiaException("Clip durationSeconds must be positive: " + durationSeconds);
        }
        channels = List.copyOf(channels);
    }

    public float wrapTime(float timeSeconds) {
        float wrapped = timeSeconds % durationSeconds;
        return wrapped < 0.0f ? wrapped + durationSeconds : wrapped;
    }
}
