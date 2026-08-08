package fr.epistudio.epysia.net.prediction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

public final class InputRing {
    private static final int CAPACITY = 128;

    private final TreeMap<Integer, InputSample> samplesByTick = new TreeMap<>();

    public void push(InputSample sample) {
        samplesByTick.put(sample.tick(), sample);
        while (samplesByTick.size() > CAPACITY) {
            samplesByTick.pollFirstEntry();
        }
    }

    public Optional<InputSample> at(int tick) {
        return Optional.ofNullable(samplesByTick.get(tick));
    }

    public Optional<InputSample> latest() {
        return samplesByTick.isEmpty() ? Optional.empty() : Optional.of(samplesByTick.lastEntry().getValue());
    }

    public List<InputSample> after(int tick) {
        return new ArrayList<>(samplesByTick.tailMap(tick, false).values());
    }

    public List<InputSample> lastSamples(int count) {
        List<InputSample> recent = new ArrayList<>(samplesByTick.values());
        int from = Math.max(0, recent.size() - count);
        return recent.subList(from, recent.size());
    }

    public void acknowledgeThrough(int tick) {
        samplesByTick.headMap(tick, true).clear();
    }

    public int size() {
        return samplesByTick.size();
    }

    public void clear() {
        samplesByTick.clear();
    }
}
