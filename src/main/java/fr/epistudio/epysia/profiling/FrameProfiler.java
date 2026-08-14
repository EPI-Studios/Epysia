package fr.epistudio.epysia.profiling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FrameProfiler {

    public static final String TICK_SECTION = "tick";
    public static final String BACKGROUND_DELIVERY_SECTION = "backgroundDelivery";
    public static final String RENDER_SECTION = "render";
    public static final String COLLECT_SECTION = "collect";
    public static final String DRAIN_SECTION = "drain";
    public static final String POLL_SECTION = "poll";
    public static final String UPDATE_SECTION = "update";
    public static final String SWAP_BUFFERS_SECTION = "swap-buffers";
    public static final String SYSTEM_PREFIX = "system/";
    public static final String COLLECT_PREFIX = "collect/";

    private static final int MAXIMUM_SPANS = 4096;

    private static final class Scope {

        private final String name;
        private final Scope parent;
        private final Map<String, Scope> children = new LinkedHashMap<>();
        private long totalNanos;
        private long enteredAtNanos;
        private int calls;

        private Scope(String name, Scope parent) {
            this.name = name;
            this.parent = parent;
        }
    }

    private final Scope root = new Scope("", null);
    private final Map<String, Long> published = new LinkedHashMap<>();
    private final Map<String, Long> readOnlyPublished = Collections.unmodifiableMap(published);
    private final List<ProfileSpan> spans = new ArrayList<>();
    private Scope current = root;
    private int depth;
    private int droppedSpans;
    private long windowStartNanos = System.nanoTime();
    private ProfileFrame publishedFrame = ProfileFrame.EMPTY;

    public void begin(String name) {
        Scope scope = childOf(current, name);
        scope.enteredAtNanos = System.nanoTime();
        scope.calls++;
        current = scope;
        depth++;
    }

    public void end() {
        if (current == root) {
            return;
        }
        long now = System.nanoTime();
        current.totalNanos += now - current.enteredAtNanos;
        depth--;
        addSpan(current.name, depth, current.enteredAtNanos, now);
        current = current.parent;
    }

    public void record(String sectionName, long nanos) {
        Scope scope = childOf(current, sectionName);
        scope.totalNanos += nanos;
        scope.calls++;
        long now = System.nanoTime();
        addSpan(sectionName, depth, now - nanos, now);
    }

    public void publishFrame() {
        long now = System.nanoTime();
        published.clear();
        List<ProfileNode> roots = new ArrayList<>(root.children.size());
        for (Scope child : root.children.values()) {
            if (child.calls > 0) {
                roots.add(snapshot(child));
            }
        }
        publishedFrame = new ProfileFrame(List.copyOf(roots), List.copyOf(spans), droppedSpans,
                windowStartNanos, now);
        reset(root);
        spans.clear();
        droppedSpans = 0;
        windowStartNanos = now;
    }

    public Map<String, Long> sections() {
        return readOnlyPublished;
    }

    public ProfileFrame frame() {
        return publishedFrame;
    }

    public long nanos(String sectionName) {
        return published.getOrDefault(sectionName, 0L);
    }

    private static Scope childOf(Scope parent, String name) {
        return parent.children.computeIfAbsent(name, key -> new Scope(key, parent));
    }

    private void addSpan(String name, int spanDepth, long startNanos, long endNanos) {
        if (spans.size() >= MAXIMUM_SPANS) {
            droppedSpans++;
            return;
        }
        spans.add(new ProfileSpan(name, spanDepth, startNanos, endNanos));
    }

    private ProfileNode snapshot(Scope scope) {
        List<ProfileNode> children = new ArrayList<>(scope.children.size());
        long childTotal = 0L;
        for (Scope child : scope.children.values()) {
            if (child.calls == 0) {
                continue;
            }
            ProfileNode node = snapshot(child);
            childTotal += node.totalNanos();
            children.add(node);
        }
        published.merge(scope.name, scope.totalNanos, Long::sum);
        return new ProfileNode(scope.name, scope.totalNanos, scope.totalNanos - childTotal,
                scope.calls, List.copyOf(children));
    }

    private static void reset(Scope scope) {
        for (Scope child : scope.children.values()) {
            child.totalNanos = 0L;
            child.calls = 0;
            reset(child);
        }
    }
}
