package fr.epistudio.epysia.render;

import fr.epistudio.epysia.render.backend.DrawCommand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Frame implements FrameBuilder {

    private static final Comparator<DrawCommand> SORT_KEY_ORDER =
            Comparator.comparingLong(DrawCommand::sortKey);

    private final List<List<DrawCommand>> buckets = new ArrayList<>();
    private static final boolean TRACK_SUBMIT_SITES =
            Boolean.getBoolean("epysia.trackBindingSets");
    private static final Map<Long, String> submitSites = new HashMap<>();

    public Frame() {
        growToRegistryCapacity();
    }

    @Override
    public void submit(RenderPass pass, DrawCommand command) {
        if (TRACK_SUBMIT_SITES) {
            submitSites.put(command.bindings().id(), pass.name() + describeSubmitSite());
        }
        bucketFor(pass).add(command);
    }

    public static String submitSiteOf(long bindingSetId) {
        String site = submitSites.get(bindingSetId);
        return site == null ? "no recorded submit site" : site;
    }

    private static String describeSubmitSite() {
        StringBuilder site = new StringBuilder();
        StackWalker.getInstance()
                .walk(frames -> frames.skip(2).limit(7).map(StackWalker.StackFrame::toString).toList())
                .forEach(frame -> site.append("\n        at ").append(frame));
        return site.toString();
    }

    public List<DrawCommand> commandsFor(RenderPass pass) {
        return bucketFor(pass);
    }

    public void sortByKey(RenderPass pass) {
        bucketFor(pass).sort(SORT_KEY_ORDER);
    }

    public void reset() {
        growToRegistryCapacity();
        for (List<DrawCommand> bucket : buckets) {
            bucket.clear();
        }
    }

    private List<DrawCommand> bucketFor(RenderPass pass) {
        if (pass.index() >= buckets.size()) {
            growToRegistryCapacity();
        }
        return buckets.get(pass.index());
    }

    private void growToRegistryCapacity() {
        while (buckets.size() < RenderPasses.capacity()) {
            buckets.add(new ArrayList<>());
        }
    }
}
