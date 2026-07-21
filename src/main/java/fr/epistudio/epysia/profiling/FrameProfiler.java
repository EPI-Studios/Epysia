package fr.epistudio.epysia.profiling;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FrameProfiler {

    public static final String TICK_SECTION = "tick";
    public static final String RENDER_SECTION = "render";
    public static final String COLLECT_SECTION = "collect";
    public static final String DRAIN_SECTION = "drain";
    public static final String SYSTEM_PREFIX = "system/";
    public static final String COLLECT_PREFIX = "collect/";

    private final Map<String, Long> accumulating = new LinkedHashMap<>();
    private final Map<String, Long> published = new LinkedHashMap<>();
    private final Map<String, Long> readOnlyPublished = Collections.unmodifiableMap(published);

    public void record(String sectionName, long nanos) {
        accumulating.merge(sectionName, nanos, Long::sum);
    }

    public void publishFrame() {
        published.clear();
        published.putAll(accumulating);
        accumulating.clear();
    }

    public Map<String, Long> sections() {
        return readOnlyPublished;
    }

    public long nanos(String sectionName) {
        return published.getOrDefault(sectionName, 0L);
    }
}
