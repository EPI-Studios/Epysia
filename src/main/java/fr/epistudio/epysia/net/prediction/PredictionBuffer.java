package fr.epistudio.epysia.net.prediction;

import java.util.Optional;
import java.util.TreeMap;

public final class PredictionBuffer {
    private static final int CAPACITY = 128;

    private final TreeMap<Integer, PredictedTransform> statesByTick = new TreeMap<>();

    public void record(int tick, PredictedTransform state) {
        statesByTick.put(tick, state);
        while (statesByTick.size() > CAPACITY) {
            statesByTick.pollFirstEntry();
        }
    }

    public Optional<PredictedTransform> at(int tick) {
        return Optional.ofNullable(statesByTick.get(tick));
    }

    public void forgetThrough(int tick) {
        statesByTick.headMap(tick, false).clear();
    }

    public void clear() {
        statesByTick.clear();
    }
}
