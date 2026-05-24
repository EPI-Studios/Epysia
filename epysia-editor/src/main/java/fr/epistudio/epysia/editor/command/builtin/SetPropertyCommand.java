package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.editor.reflection.ExportedProperty;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SetPropertyCommand implements EditorCommand {

    private final ExportedProperty property;
    private final Object value;
    private final String coalesceKey;
    private final String label;

    public SetPropertyCommand(ExportedProperty property, Object value, String coalesceKey) {
        this.property = property;
        this.value = cloneIfMutable(value);
        this.coalesceKey = coalesceKey;
        this.label = "Set " + property.label();
    }

    public static SetPropertyCommand coalescing(ExportedProperty property, Object value, String groupKey) {
        return new SetPropertyCommand(property, value,
                "prop:" + groupKey + ":" + property.fieldName());
    }

    public static SetPropertyCommand discrete(ExportedProperty property, Object value) {
        return new SetPropertyCommand(property, value, null);
    }

    @Override
    public void apply(CommandContext context) {
        switch (property.kind()) {
            case FLOAT -> property.writeFloat(((Number) value).floatValue());
            case INT -> property.writeInt(((Number) value).intValue());
            case BOOLEAN -> property.writeBoolean((Boolean) value);
            case STRING -> property.writeObject(value);
            case VECTOR3 -> ((Vector3f) property.read()).set((Vector3f) value);
            case QUATERNION -> ((Quaternionf) property.read()).set((Quaternionf) value).normalize();
            case UNKNOWN -> property.writeObject(value);
        }
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        Object current = property.read();
        return new SetPropertyCommand(property, current, coalesceKey);
    }

    @Override
    public String coalesceKey() {
        return coalesceKey;
    }

    @Override
    public String label() {
        return label;
    }

    private static Object cloneIfMutable(Object value) {
        if (value instanceof Vector3f vector) {
            return new Vector3f(vector);
        }
        if (value instanceof Quaternionf quaternion) {
            return new Quaternionf(quaternion);
        }
        return value;
    }
}
