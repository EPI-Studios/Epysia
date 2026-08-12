package fr.epistudio.epysia.worldgen;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.concurrent.BackgroundTask;
import fr.epistudio.epysia.exceptions.EpysiaException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LayerWorld {
    private static final int DEFAULT_MAXIMUM_ATTACHES_PER_FRAME = 1;
    private static final int DEFAULT_MAXIMUM_IN_FLIGHT = 12;

    private static final class ChunkSlot {
        private Optional<Object> data = Optional.empty();
        private Optional<BackgroundTask<Object>> pending = Optional.empty();
        private boolean attached;
    }

    private record Requirements(Map<GenerationLayer<?>, Set<ChunkCoordinate>> visible,
                                Map<GenerationLayer<?>, Set<ChunkCoordinate>> all) {
    }

    private final Map<GenerationLayer<?>, LayerGrid> grids = new LinkedHashMap<>();
    private final Map<GenerationLayer<?>, Map<ChunkCoordinate, ChunkSlot>> slots = new LinkedHashMap<>();
    private final Map<GenerationLayer<?>, Float> topLevelRadius = new LinkedHashMap<>();
    private final Map<GenerationLayer<?>, Set<ChunkCoordinate>> visibleChunks = new LinkedHashMap<>();
    private final List<GenerationLayer<?>> order = new ArrayList<>();
    private int maximumInFlight = DEFAULT_MAXIMUM_IN_FLIGHT;
    private int maximumAttachesPerFrame = DEFAULT_MAXIMUM_ATTACHES_PER_FRAME;
    private int inFlight;

    private static final float RELEASE_MARGIN = 1.18f;

    public void addLayer(GenerationLayer<?> layer) {
        if (grids.containsKey(layer)) {
            return;
        }
        grids.put(layer, new LayerGrid(layer.chunkSize()));
        slots.put(layer, new HashMap<>());
        rebuildOrder();
    }

    public void require(GenerationLayer<?> layer, float radius) {
        addLayer(layer);
        topLevelRadius.put(layer, radius);
    }

    public void setMaximumInFlight(int value) {
        this.maximumInFlight = Math.max(1, value);
    }

    public int readyChunkCount() {
        return slots.values().stream()
                .mapToInt(layerSlots -> (int) layerSlots.values().stream()
                        .filter(slot -> slot.data.isPresent()).count())
                .sum();
    }

    public int pendingChunkCount() {
        return inFlight;
    }

    public void update(EngineServices services, float focusX, float focusZ) {
        Requirements requirements = computeRequired(focusX, focusZ);
        visibleChunks.clear();
        visibleChunks.putAll(requirements.visible());
        releaseUnrequired(services, requirements.all());
        reconcileAttachments(services);
        scheduleMissing(services, requirements.all(), focusX, focusZ);
    }

    private void reconcileAttachments(EngineServices services) {
        int budget = maximumAttachesPerFrame;
        for (GenerationLayer<?> layer : order) {
            Set<ChunkCoordinate> visible = visibleChunks.getOrDefault(layer, Set.of());
            for (Map.Entry<ChunkCoordinate, ChunkSlot> entry : slots.get(layer).entrySet()) {
                budget = reconcileSlot(services, layer, entry.getKey(), entry.getValue(),
                        visible.contains(entry.getKey()), budget);
            }
        }
    }

    private int reconcileSlot(EngineServices services, GenerationLayer<?> layer,
                              ChunkCoordinate coordinate, ChunkSlot slot, boolean visible,
                              int budget) {
        if (slot.data.isEmpty() || visible == slot.attached) {
            return budget;
        }
        if (!visible) {
            detach(services, layer, coordinate, slot);
            return budget;
        }
        if (budget <= 0) {
            return budget;
        }
        attach(services, layer, coordinate, slot);
        return budget - 1;
    }

    public void setMaximumAttachesPerFrame(int value) {
        this.maximumAttachesPerFrame = Math.max(1, value);
    }

    public int maximumAttachesPerFrame() {
        return maximumAttachesPerFrame;
    }

    private void rebuildOrder() {
        order.clear();
        Set<GenerationLayer<?>> visited = new HashSet<>();
        Set<GenerationLayer<?>> visiting = new LinkedHashSet<>();
        for (GenerationLayer<?> layer : grids.keySet()) {
            visit(layer, visited, visiting);
        }
    }

    private void visit(GenerationLayer<?> layer, Set<GenerationLayer<?>> visited,
                       Set<GenerationLayer<?>> visiting) {
        if (visited.contains(layer)) {
            return;
        }
        if (!visiting.add(layer)) {
            throw new EpysiaException("Layer dependency cycle involving " + layer.name());
        }
        for (LayerDependency dependency : layer.dependencies()) {
            if (!grids.containsKey(dependency.layer())) {
                addLayerDuringVisit(dependency.layer());
            }
            visit(dependency.layer(), visited, visiting);
        }
        visiting.remove(layer);
        visited.add(layer);
        order.add(layer);
    }

    private void addLayerDuringVisit(GenerationLayer<?> layer) {
        grids.put(layer, new LayerGrid(layer.chunkSize()));
        slots.put(layer, new HashMap<>());
    }

    private Requirements computeRequired(float focusX, float focusZ) {
        Map<GenerationLayer<?>, Set<ChunkCoordinate>> visible = new LinkedHashMap<>();
        Map<GenerationLayer<?>, Set<ChunkCoordinate>> all = new LinkedHashMap<>();
        for (GenerationLayer<?> layer : order) {
            visible.put(layer, new LinkedHashSet<>());
            all.put(layer, new LinkedHashSet<>());
        }
        for (Map.Entry<GenerationLayer<?>, Float> entry : topLevelRadius.entrySet()) {
            GenerationLayer<?> layer = entry.getKey();
            WorldRect area = WorldRect.around(focusX, focusZ, entry.getValue());
            Set<ChunkCoordinate> wanted = visible.get(layer);
            wanted.addAll(grids.get(layer).covering(area));
            retainAttachedWithinMargin(layer, focusX, focusZ, entry.getValue(), wanted);
        }
        order.forEach(layer -> all.get(layer).addAll(visible.get(layer)));
        for (int index = order.size() - 1; index >= 0; index--) {
            propagateRequirements(order.get(index), all);
        }
        return new Requirements(visible, all);
    }

    private void retainAttachedWithinMargin(GenerationLayer<?> layer, float focusX, float focusZ,
                                            float radius, Set<ChunkCoordinate> wanted) {
        Map<ChunkCoordinate, ChunkSlot> layerSlots = slots.get(layer);
        if (layerSlots == null || layerSlots.isEmpty()) {
            return;
        }
        WorldRect keepArea = WorldRect.around(focusX, focusZ, radius * RELEASE_MARGIN);
        Set<ChunkCoordinate> keep = new LinkedHashSet<>(grids.get(layer).covering(keepArea));
        for (Map.Entry<ChunkCoordinate, ChunkSlot> entry : layerSlots.entrySet()) {
            if (entry.getValue().attached && keep.contains(entry.getKey())) {
                wanted.add(entry.getKey());
            }
        }
    }

    private void propagateRequirements(GenerationLayer<?> layer,
                                       Map<GenerationLayer<?>, Set<ChunkCoordinate>> required) {
        LayerGrid grid = grids.get(layer);
        for (ChunkCoordinate coordinate : required.get(layer)) {
            for (LayerDependency dependency : layer.dependencies()) {
                WorldRect context = grid.boundsOf(coordinate).expanded(dependency.padding());
                required.get(dependency.layer())
                        .addAll(grids.get(dependency.layer()).covering(context));
            }
        }
    }

    private void releaseUnrequired(EngineServices services,
                                   Map<GenerationLayer<?>, Set<ChunkCoordinate>> required) {
        for (GenerationLayer<?> layer : order) {
            Map<ChunkCoordinate, ChunkSlot> layerSlots = slots.get(layer);
            Set<ChunkCoordinate> keep = required.get(layer);
            List<ChunkCoordinate> expired = layerSlots.keySet().stream()
                    .filter(coordinate -> !keep.contains(coordinate))
                    .toList();
            for (ChunkCoordinate coordinate : expired) {
                release(services, layer, layerSlots, coordinate);
            }
        }
    }

    private void release(EngineServices services, GenerationLayer<?> layer,
                         Map<ChunkCoordinate, ChunkSlot> layerSlots, ChunkCoordinate coordinate) {
        ChunkSlot slot = layerSlots.remove(coordinate);
        slot.pending.ifPresent(task -> {
            task.cancel();
            inFlight--;
        });
        detach(services, layer, coordinate, slot);
    }

    @SuppressWarnings("unchecked")
    private <T> void attach(EngineServices services, GenerationLayer<T> layer,
                            ChunkCoordinate coordinate, ChunkSlot slot) {
        if (slot.attached || slot.data.isEmpty()) {
            return;
        }
        slot.attached = true;
        layer.attach(coordinate, (T) slot.data.get(), services);
    }

    @SuppressWarnings("unchecked")
    private <T> void detach(EngineServices services, GenerationLayer<T> layer,
                            ChunkCoordinate coordinate, ChunkSlot slot) {
        if (!slot.attached || slot.data.isEmpty()) {
            return;
        }
        slot.attached = false;
        layer.detach(coordinate, (T) slot.data.get(), services);
    }

    private void scheduleMissing(EngineServices services,
                                 Map<GenerationLayer<?>, Set<ChunkCoordinate>> required,
                                 float focusX, float focusZ) {
        for (GenerationLayer<?> layer : order) {
            if (inFlight >= maximumInFlight) {
                return;
            }
            scheduleLayer(services, layer, required.get(layer), focusX, focusZ);
        }
    }

    private void scheduleLayer(EngineServices services, GenerationLayer<?> layer,
                               Set<ChunkCoordinate> requiredChunks, float focusX, float focusZ) {
        Map<ChunkCoordinate, ChunkSlot> layerSlots = slots.get(layer);
        LayerGrid grid = grids.get(layer);
        List<ChunkCoordinate> missing = requiredChunks.stream()
                .filter(coordinate -> !layerSlots.containsKey(coordinate))
                .sorted(Comparator.comparingDouble(coordinate -> distanceTo(grid, coordinate, focusX, focusZ)))
                .toList();
        for (ChunkCoordinate coordinate : missing) {
            if (inFlight >= maximumInFlight) {
                return;
            }
            submitIfReady(services, layer, layerSlots, grid, coordinate);
        }
    }

    private static double distanceTo(LayerGrid grid, ChunkCoordinate coordinate, float focusX, float focusZ) {
        WorldRect bounds = grid.boundsOf(coordinate);
        double deltaX = bounds.centreX() - focusX;
        double deltaZ = bounds.centreZ() - focusZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private void submitIfReady(EngineServices services, GenerationLayer<?> layer,
                               Map<ChunkCoordinate, ChunkSlot> layerSlots, LayerGrid grid,
                               ChunkCoordinate coordinate) {
        Optional<Map<GenerationLayer<?>, Map<ChunkCoordinate, Object>>> context =
                collectContext(layer, grid.boundsOf(coordinate));
        if (context.isEmpty()) {
            return;
        }
        ChunkSlot slot = new ChunkSlot();
        layerSlots.put(coordinate, slot);
        inFlight++;
        slot.pending = Optional.of(submit(services, layer, coordinate,
                new LayerContext(grid.boundsOf(coordinate), context.get(), grids), slot));
    }

    private Optional<Map<GenerationLayer<?>, Map<ChunkCoordinate, Object>>> collectContext(
            GenerationLayer<?> layer, WorldRect bounds) {
        Map<GenerationLayer<?>, Map<ChunkCoordinate, Object>> context = new LinkedHashMap<>();
        for (LayerDependency dependency : layer.dependencies()) {
            Map<ChunkCoordinate, Object> visible = new LinkedHashMap<>();
            Map<ChunkCoordinate, ChunkSlot> dependencySlots = slots.get(dependency.layer());
            for (ChunkCoordinate needed : grids.get(dependency.layer())
                    .covering(bounds.expanded(dependency.padding()))) {
                ChunkSlot dependencySlot = dependencySlots.get(needed);
                if (dependencySlot == null || dependencySlot.data.isEmpty()) {
                    return Optional.empty();
                }
                visible.put(needed, dependencySlot.data.get());
            }
            context.put(dependency.layer(), visible);
        }
        return Optional.of(context);
    }

    @SuppressWarnings("unchecked")
    private <T> BackgroundTask<Object> submit(EngineServices services, GenerationLayer<T> layer,
                                              ChunkCoordinate coordinate, LayerContext context,
                                              ChunkSlot slot) {
        return (BackgroundTask<Object>) (BackgroundTask<?>) services.backgroundTasks().submit(
                () -> layer.generate(coordinate, context),
                data -> adopt(services, layer, coordinate, data, slot),
                failure -> failGeneration(services, layer, coordinate, slot, failure));
    }

    private <T> void adopt(EngineServices services, GenerationLayer<T> layer, ChunkCoordinate coordinate,
                           T data, ChunkSlot slot) {
        inFlight--;
        slot.pending = Optional.empty();
        slot.data = Optional.of(data);
        if (visibleChunks.getOrDefault(layer, Set.of()).contains(coordinate)) {
            attach(services, layer, coordinate, slot);
        }
    }

    private void failGeneration(EngineServices services, GenerationLayer<?> layer,
                                ChunkCoordinate coordinate, ChunkSlot slot, Throwable failure) {
        inFlight--;
        slot.pending = Optional.empty();
        slots.get(layer).remove(coordinate);
        services.logger().error("[LayerWorld] " + layer.name() + " failed to generate " + coordinate, failure);
    }

    public void shutdown(EngineServices services) {
        for (int index = order.size() - 1; index >= 0; index--) {
            GenerationLayer<?> layer = order.get(index);
            Map<ChunkCoordinate, ChunkSlot> layerSlots = slots.get(layer);
            for (ChunkCoordinate coordinate : List.copyOf(layerSlots.keySet())) {
                release(services, layer, layerSlots, coordinate);
            }
            layer.shutdown(services);
        }
        inFlight = 0;
    }
}
