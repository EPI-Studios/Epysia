package fr.epistudio.epysia.net.replication;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class InterpolationBuffer {
    private static final int MAXIMUM_SAMPLES = 32;

    private final Deque<Sample> samples = new ArrayDeque<>();

    public void push(int tick, WorldState.ObjectState state) {
        if (!samples.isEmpty() && samples.peekLast().tick() >= tick) {
            return;
        }
        samples.addLast(new Sample(tick, state));
        while (samples.size() > MAXIMUM_SAMPLES) {
            samples.removeFirst();
        }
    }

    public Optional<Blend> blendAt(float tick) {
        Sample previous = null;
        for (Sample sample : samples) {
            if (sample.tick() >= tick && previous != null) {
                return Optional.of(blendBetween(previous, sample, tick));
            }
            previous = sample;
        }
        return Optional.ofNullable(previous).map(latest -> new Blend(latest.state(), latest.state(), 1.0f));
    }

    private static Blend blendBetween(Sample from, Sample to, float tick) {
        int span = to.tick() - from.tick();
        float alpha = span <= 0 ? 1.0f : Math.clamp((tick - from.tick()) / span, 0.0f, 1.0f);
        return new Blend(from.state(), to.state(), alpha);
    }

    public Optional<Integer> latestTick() {
        return samples.isEmpty() ? Optional.empty() : Optional.of(samples.peekLast().tick());
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    public void clear() {
        samples.clear();
    }

    public record Blend(WorldState.ObjectState from, WorldState.ObjectState to, float alpha) {
    }

    private record Sample(int tick, WorldState.ObjectState state) {
    }
}
