package fr.epistudio.epysia.editor.preview;

import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class NodePreviewCache {

    public static final int MAX_LIVE_TARGETS = 64;
    public static final int MAX_REBUILDS_PER_FRAME = 1;
    public static final int MAX_ANIMATIONS_PER_FRAME = 2;

    private final Map<PreviewKey, NodePreviewEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Deque<Integer> freeSlots = new ArrayDeque<>();
    private final List<PreviewRenderTarget> retiredTargets = new ArrayList<>();
    private int rebuildBudget = MAX_REBUILDS_PER_FRAME;
    private int animationBudget = MAX_ANIMATIONS_PER_FRAME;

    public NodePreviewCache() {
        for (int slot = 0; slot < MAX_LIVE_TARGETS; slot++) {
            freeSlots.add(slot);
        }
    }

    public void beginFrame(OpenGlRenderBackend backend) {
        releaseRetiredTargets(backend);
        rebuildBudget = MAX_REBUILDS_PER_FRAME;
        animationBudget = MAX_ANIMATIONS_PER_FRAME;
    }

    private void retire(NodePreviewEntry entry) {
        retiredTargets.add(entry.target());
        freeSlots.addLast(entry.slot());
    }

    private void releaseRetiredTargets(OpenGlRenderBackend backend) {
        for (PreviewRenderTarget target : retiredTargets) {
            target.destroy(backend);
        }
        retiredTargets.clear();
    }

    public boolean canRebuild() {
        return rebuildBudget > 0;
    }

    public void consumeRebuild() {
        rebuildBudget--;
    }

    public boolean canAnimate() {
        return animationBudget > 0;
    }

    public void consumeAnimation() {
        animationBudget--;
    }

    public Optional<NodePreviewEntry> find(PreviewKey key) {
        return Optional.ofNullable(entries.get(key));
    }

    public int liveTargetCount() {
        return entries.size();
    }

    public boolean hasRoomFor(PreviewKey key) {
        return entries.containsKey(key) || !freeSlots.isEmpty() || !entries.isEmpty();
    }

    public NodePreviewEntry claim(PreviewKey key, OpenGlRenderBackend backend, int pixelSize) {
        NodePreviewEntry existing = entries.get(key);
        if (existing != null) {
            return existing;
        }
        evictUntilRoomAvailable();
        int slot = freeSlots.removeFirst();
        NodePreviewEntry created = new NodePreviewEntry(slot, PreviewRenderTarget.create(backend, pixelSize, pixelSize));
        entries.put(key, created);
        return created;
    }

    private void evictUntilRoomAvailable() {
        while (freeSlots.isEmpty() && !entries.isEmpty()) {
            Iterator<Map.Entry<PreviewKey, NodePreviewEntry>> iterator = entries.entrySet().iterator();
            NodePreviewEntry eldest = iterator.next().getValue();
            iterator.remove();
            retire(eldest);
        }
    }

    public void invalidateGraph(Path graphPath) {
        Iterator<Map.Entry<PreviewKey, NodePreviewEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PreviewKey, NodePreviewEntry> entry = iterator.next();
            if (!entry.getKey().graphPath().equals(graphPath)) {
                continue;
            }
            iterator.remove();
            retire(entry.getValue());
        }
    }

    public void shutdown(OpenGlRenderBackend backend) {
        releaseRetiredTargets(backend);
        for (NodePreviewEntry entry : entries.values()) {
            entry.target().destroy(backend);
        }
        entries.clear();
        freeSlots.clear();
        for (int slot = 0; slot < MAX_LIVE_TARGETS; slot++) {
            freeSlots.add(slot);
        }
    }

    public record PreviewKey(Path graphPath, int nodeId, String pinName) {
    }
}
