package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.net.protocol.NetWriter;

import java.util.List;

public final class StateCapture {
    private static final int CUSTOM_STATE_CAPACITY = 1024;

    private final ReplicationTable table;

    public StateCapture(ReplicationTable table) {
        this.table = table;
    }

    public void capture(GameObject gameObject, int networkId, WorldState into) {
        WorldState.ObjectState objectState = into.objectFor(networkId);
        for (IComponent component : gameObject.components()) {
            int componentIndex = table.indexOf(component.getClass());
            if (componentIndex >= 0) {
                captureComponent(component, componentIndex, objectState);
            }
        }
    }

    private void captureComponent(IComponent component, int componentIndex, WorldState.ObjectState objectState) {
        component.onReplicatedStateCapture();
        if (component instanceof NetworkSerializable serializable) {
            objectState.put(componentIndex, WorldState.ComponentState.ofCustomPayload(encode(serializable)));
            return;
        }
        List<ReplicatedField> fields = table.fieldsFor(component.getClass());
        WorldState.ComponentState state = objectState.componentFor(componentIndex, fields.size());
        for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
            state.setValueAt(fieldIndex, fields.get(fieldIndex).read(component));
        }
    }

    private static byte[] encode(NetworkSerializable serializable) {
        NetWriter writer = NetWriter.allocate(CUSTOM_STATE_CAPACITY);
        serializable.writeState(writer);
        return writer.toByteArray();
    }
}
