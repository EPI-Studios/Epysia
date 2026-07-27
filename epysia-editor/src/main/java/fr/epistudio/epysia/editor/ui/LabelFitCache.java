package fr.epistudio.epysia.editor.ui;

import imgui.ImGui;

import java.util.HashMap;
import java.util.Map;

public final class LabelFitCache {

    private static final String ELLIPSIS = "...";
    private static final int MAXIMUM_ENTRIES = 4096;
    private static final float WIDTH_QUANTUM = 4.0f;

    public record LabelFit(String label, boolean truncated) {
    }

    private final Map<String, LabelFit> cache = new HashMap<>();
    private int cachedWidthBucket = Integer.MIN_VALUE;

    public LabelFit fitFor(String name, float availableWidth) {
        int bucket = (int) (availableWidth / WIDTH_QUANTUM);
        if (bucket != cachedWidthBucket) {
            cache.clear();
            cachedWidthBucket = bucket;
        }
        if (cache.size() >= MAXIMUM_ENTRIES) {
            cache.clear();
        }
        return cache.computeIfAbsent(name, key -> measure(key, bucket * WIDTH_QUANTUM));
    }

    private static LabelFit measure(String name, float availableWidth) {
        if (ImGui.calcTextSize(name).x <= availableWidth) {
            return new LabelFit(name, false);
        }
        return new LabelFit(longestFittingPrefix(name, availableWidth) + ELLIPSIS, true);
    }

    private static String longestFittingPrefix(String name, float availableWidth) {
        int low = 1;
        int high = name.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (ImGui.calcTextSize(name.substring(0, middle) + ELLIPSIS).x <= availableWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return name.substring(0, low);
    }
}
