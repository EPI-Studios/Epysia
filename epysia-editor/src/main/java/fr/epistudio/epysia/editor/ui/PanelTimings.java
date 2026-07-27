package fr.epistudio.epysia.editor.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PanelTimings {

    public record Entry(String name, float milliseconds) {
    }

    private static final float SMOOTHING = 0.1f;

    private final Map<String, Float> averages = new LinkedHashMap<>();
    private final Map<String, Long> currentNanos = new LinkedHashMap<>();

    public void beginFrame() {
        currentNanos.clear();
    }

    public void measure(String name, Runnable panel) {
        long start = System.nanoTime();
        panel.run();
        long elapsed = System.nanoTime() - start;
        currentNanos.merge(name, elapsed, Long::sum);
        float milliseconds = elapsed / 1_000_000.0f;
        averages.merge(name, milliseconds,
                (previous, sample) -> previous + (sample - previous) * SMOOTHING);
    }

    public List<Entry> ordered() {
        List<Entry> entries = new ArrayList<>(averages.size());
        for (Map.Entry<String, Float> entry : averages.entrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue()));
        }
        entries.sort((first, second) -> Float.compare(second.milliseconds(), first.milliseconds()));
        return entries;
    }

    public float totalMilliseconds() {
        float total = 0.0f;
        for (float value : averages.values()) {
            total += value;
        }
        return total;
    }
}
