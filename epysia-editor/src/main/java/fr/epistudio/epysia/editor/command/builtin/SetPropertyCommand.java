package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.reflection.ExportedProperty;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SetPropertyCommand implements EditorCommand {

    private final IComponent owner;
    private final ExportedProperty property;
    private final Object beforeValue;
    private final Object afterValue;

    public SetPropertyCommand(IComponent owner, ExportedProperty property,
                              Object beforeValue, Object afterValue) {
        this.owner = owner;
        this.property = property;
        this.beforeValue = snapshot(beforeValue);
        this.afterValue = snapshot(afterValue);
    }

    @Override
    public void apply(CommandContext context) {
        writeValue(afterValue);
        if (owner instanceof Transform3D transform) {
            transform.markDirty();
        }
        if (property.kind() == ExportedProperty.Kind.ASSET_REF) {
            owner.onLoad(context.services());
        }
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new SetPropertyCommand(owner, property, afterValue, beforeValue);
    }

    @Override
    public String coalesceKey() {
        return "set:" + System.identityHashCode(owner) + "." + property.fieldName();
    }

    @Override
    public String label() {
        return "Set " + property.label();
    }

    private void writeValue(Object value) {
        switch (property.kind()) {
            case FLOAT -> property.writeFloat(((Number) value).floatValue());
            case INT -> property.writeInt(((Number) value).intValue());
            case BOOLEAN -> property.writeBoolean((Boolean) value);
            case STRING, ENUM -> property.writeObject(value);
            case GAMEOBJECT_REF -> property.writeObject(value);
            case VECTOR3 -> {
                Vector3f existing = (Vector3f) property.read();
                Vector3f source = (Vector3f) value;
                existing.set(source);
            }
            case QUATERNION -> {
                Quaternionf existing = (Quaternionf) property.read();
                Quaternionf source = (Quaternionf) value;
                existing.set(source);
            }
            case ASSET_REF -> {
                AssetRef<?> existing = (AssetRef<?>) property.read();
                existing.setPath((String) value);
            }
            default -> {
            }
        }
    }

    private Object snapshot(Object value) {
        return switch (property.kind()) {
            case VECTOR3 -> new Vector3f((Vector3f) value);
            case QUATERNION -> new Quaternionf((Quaternionf) value);
            case ASSET_REF -> value instanceof AssetRef<?> ref ? ref.path() : (String) value;
            case GAMEOBJECT_REF -> value;
            default -> value;
        };
    }
}
