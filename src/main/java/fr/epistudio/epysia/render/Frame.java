package fr.epistudio.epysia.render;

import fr.epistudio.epysia.render.backend.DrawCommand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Frame implements FrameBuilder {

    private static final Comparator<DrawCommand> SORT_KEY_ORDER =
            Comparator.comparingLong(DrawCommand::sortKey);

    private final List<List<DrawCommand>> buckets = new ArrayList<>();

    public Frame() {
        growToRegistryCapacity();
    }

    @Override
    public void submit(RenderPass pass, DrawCommand command) {
        bucketFor(pass).add(command);
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
