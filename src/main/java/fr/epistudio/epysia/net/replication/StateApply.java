package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.net.protocol.NetReader;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class StateApply {
    private final ReplicationTable table;

    public StateApply(ReplicationTable table) {
        this.table = table;
    }

    public void apply(GameObject gameObject, WorldState.ObjectState state, Set<Class<?>> excludedTypes) {
        for (IComponent component : gameObject.components()) {
            int componentIndex = table.indexOf(component.getClass());
            if (componentIndex < 0 || excludedTypes.contains(component.getClass())) {
                continue;
            }
            state.find(componentIndex).ifPresent(componentState ->
                    applyComponent(component, componentIndex, componentState));
        }
    }

    public void applyBlended(GameObject gameObject, WorldState.ObjectState from,
                             WorldState.ObjectState to, float alpha, Set<Class<?>> excludedTypes) {
        for (IComponent component : gameObject.components()) {
            int componentIndex = table.indexOf(component.getClass());
            if (componentIndex < 0 || excludedTypes.contains(component.getClass())) {
                continue;
            }
            applyBlendedComponent(component, componentIndex, from.find(componentIndex),
                    to.find(componentIndex), alpha);
        }
    }

    private void applyBlendedComponent(IComponent component, int componentIndex,
                                       Optional<WorldState.ComponentState> from,
                                       Optional<WorldState.ComponentState> to, float alpha) {
        if (to.isEmpty()) {
            return;
        }
        if (from.isEmpty() || to.get().hasCustomPayload()) {
            applyComponent(component, componentIndex, to.get());
            return;
        }
        blendFields(component, from.get(), to.get(), alpha);
    }

    private void blendFields(IComponent component, WorldState.ComponentState from,
                             WorldState.ComponentState to, float alpha) {
        List<ReplicatedField> fields = table.fieldsFor(component.getClass());
        for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
            Object target = to.valueAt(fieldIndex);
            if (target == WorldState.ABSENT) {
                continue;
            }
            ReplicatedField field = fields.get(fieldIndex);
            field.write(component, field.blend(from.valueAt(fieldIndex), target, alpha));
        }
        component.onReplicatedStateApplied();
    }

    private void applyComponent(IComponent component, int componentIndex, WorldState.ComponentState state) {
        if (component instanceof NetworkSerializable serializable && state.hasCustomPayload()) {
            byte[] payload = state.customPayload();
            serializable.readState(NetReader.wrapping(payload, 0, payload.length));
            component.onReplicatedStateApplied();
            return;
        }
        List<ReplicatedField> fields = table.fieldsFor(component.getClass());
        for (int fieldIndex = 0; fieldIndex < fields.size() && fieldIndex < state.fieldCount(); fieldIndex++) {
            Object value = state.valueAt(fieldIndex);
            if (value != WorldState.ABSENT) {
                fields.get(fieldIndex).write(component, value);
            }
        }
        component.onReplicatedStateApplied();
    }
}
