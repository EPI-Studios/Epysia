package fr.epistudio.epysia.editor.ui;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SectionAverages {

    private final Map<String, FrameTimeHistory> histories = new LinkedHashMap<>();
    private final int windowLength;

    public SectionAverages(int windowLength) {
        this.windowLength = windowLength;
    }

    public void record(String sectionName, float milliseconds) {
        histories.computeIfAbsent(sectionName, ignored -> new FrameTimeHistory(windowLength))
                .record(milliseconds);
    }

    public float average(String sectionName) {
        FrameTimeHistory history = histories.get(sectionName);
        return history == null ? 0.0f : history.average();
    }
}
