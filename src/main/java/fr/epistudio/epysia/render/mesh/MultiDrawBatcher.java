package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MultiDrawBatcher {

    private static final int INITIAL_COMMAND_CAPACITY = 256;

    public record GroupKey(PipelineHandle pipeline, BindingSetHandle bindings, MeshArena arena,
                           BufferHandle perDrawBuffer) {
    }

    private static final class Group {

        private final List<ArenaMesh> allocations = new ArrayList<>();
        private final List<Integer> perDrawIndices = new ArrayList<>();
        private MultiDrawBuffer commands;
        private long sortKey;
    }

    private final RenderBackend backend;
    private final Map<GroupKey, Group> groups = new LinkedHashMap<>();

    public MultiDrawBatcher(RenderBackend backend) {
        this.backend = backend;
    }

    public void beginFrame() {
        for (Group group : groups.values()) {
            group.allocations.clear();
            group.perDrawIndices.clear();
        }
    }

    public void add(GroupKey key, ArenaMesh allocation, int perDrawIndex, long sortKey) {
        Group group = groups.computeIfAbsent(key, ignored -> new Group());
        group.allocations.add(allocation);
        group.perDrawIndices.add(perDrawIndex);
        group.sortKey = sortKey;
    }

    public int groupCount() {
        return groups.size();
    }

    public int pendingDrawCount() {
        int pending = 0;
        for (Group group : groups.values()) {
            pending += group.allocations.size();
        }
        return pending;
    }

    public List<DrawCommand> flush() {
        List<DrawCommand> commands = new ArrayList<>(groups.size());
        for (Map.Entry<GroupKey, Group> entry : groups.entrySet()) {
            buildCommand(entry.getKey(), entry.getValue()).ifPresent(commands::add);
        }
        return commands;
    }

    private Optional<DrawCommand> buildCommand(GroupKey key, Group group) {
        if (group.allocations.isEmpty()) {
            return Optional.empty();
        }
        MultiDrawBuffer buffer = bufferFor(group, group.allocations.size());
        buffer.begin();
        for (int index = 0; index < group.allocations.size(); index++) {
            buffer.append(group.allocations.get(index), group.perDrawIndices.get(index));
        }
        buffer.upload();
        return Optional.of(DrawCommand.multiDrawIndirect(key.pipeline(), key.arena().boundMesh(),
                key.bindings(), group.sortKey, buffer.buffer(), buffer.drawCount(), key.perDrawBuffer()));
    }

    private MultiDrawBuffer bufferFor(Group group, int neededCapacity) {
        if (group.commands != null && group.commands.capacity() >= neededCapacity) {
            return group.commands;
        }
        if (group.commands != null) {
            group.commands.destroy();
        }
        int capacity = Math.max(INITIAL_COMMAND_CAPACITY, neededCapacity);
        group.commands = MultiDrawBuffer.create(backend, capacity);
        return group.commands;
    }

    public void destroy() {
        for (Group group : groups.values()) {
            if (group.commands != null) {
                group.commands.destroy();
            }
        }
        groups.clear();
    }
}
