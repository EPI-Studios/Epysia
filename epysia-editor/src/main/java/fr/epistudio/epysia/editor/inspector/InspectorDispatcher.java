package fr.epistudio.epysia.editor.inspector;

import fr.epistudio.epysia.editor.EditorWorld;
import fr.epistudio.epysia.editor.command.builtin.SetPropertyCommand;
import fr.epistudio.epysia.editor.reflection.ExportedProperty;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class InspectorDispatcher {

    private final EditorWorld world;

    public InspectorDispatcher(EditorWorld world) {
        this.world = world;
    }

    public void writeFloatIfChanged(ExportedProperty property, float candidate, String groupKey) {
        float current = (float) property.read();
        if (Float.compare(current, candidate) == 0) {
            return;
        }
        world.history().execute(SetPropertyCommand.coalescing(property, candidate, groupKey));
    }

    public void writeIntIfChanged(ExportedProperty property, int candidate, String groupKey) {
        int current = (int) property.read();
        if (current == candidate) {
            return;
        }
        world.history().execute(SetPropertyCommand.coalescing(property, candidate, groupKey));
    }

    public void writeBooleanIfChanged(ExportedProperty property, boolean candidate) {
        boolean current = (boolean) property.read();
        if (current == candidate) {
            return;
        }
        world.history().execute(SetPropertyCommand.discrete(property, candidate));
    }

    public void writeStringIfChanged(ExportedProperty property, String candidate, String groupKey) {
        String current = (String) property.read();
        if (java.util.Objects.equals(current, candidate)) {
            return;
        }
        world.history().execute(SetPropertyCommand.coalescing(property, candidate, groupKey));
    }

    public void writeVector3IfChanged(ExportedProperty property, float x, float y, float z, String groupKey) {
        Vector3f current = (Vector3f) property.read();
        if (Float.compare(current.x, x) == 0
                && Float.compare(current.y, y) == 0
                && Float.compare(current.z, z) == 0) {
            return;
        }
        world.history().execute(SetPropertyCommand.coalescing(property, new Vector3f(x, y, z), groupKey));
    }

    public void writeQuaternionIfChanged(ExportedProperty property, Quaternionf candidate, String groupKey) {
        Quaternionf current = (Quaternionf) property.read();
        if (Float.compare(current.x, candidate.x) == 0
                && Float.compare(current.y, candidate.y) == 0
                && Float.compare(current.z, candidate.z) == 0
                && Float.compare(current.w, candidate.w) == 0) {
            return;
        }
        world.history().execute(SetPropertyCommand.coalescing(property, new Quaternionf(candidate), groupKey));
    }
}
