package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.assets.AssetRegistry;
import fr.epistudio.epysia.assets.AssetUri;
import fr.epistudio.epysia.assets.LegacyAssetReferences;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;
import fr.epistudio.epysia.reflection.ExportedProperty;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

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
        if (property.kind() == ExportedProperty.Kind.ASSET_REF) {
            writeAssetRef(context.services().assets(), (String) afterValue);
        } else {
            writeValue(afterValue);
        }
        if (owner instanceof Transform3D transform) {
            transform.markDirty();
        }
        if (owner instanceof Transform2D transform) {
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
        return "set:" + System.identityHashCode(property.owner()) + "." + property.fieldName();
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
            case VECTOR2 -> {
                Vector2f existing = (Vector2f) property.read();
                Vector2f source = (Vector2f) value;
                existing.set(source);
            }
            case VECTOR4 -> {
                Vector4f existing = (Vector4f) property.read();
                existing.set((Vector4f) value);
            }
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
            default -> {
            }
        }
    }

    private void writeAssetRef(AssetRegistry assets, String storedPath) {
        if (!(property.read() instanceof AssetRef<?> reference)) {
            return;
        }
        AssetUri uri = LegacyAssetReferences.interpretWithoutMigration(storedPath, assets.locator());
        reference.setReference(uri, guidFor(assets, uri));
    }

    private static String guidFor(AssetRegistry assets, AssetUri uri) {
        return assets.database().flatMap(database -> database.guidForPath(uri.path())).orElse("");
    }

    private Object snapshot(Object value) {
        return switch (property.kind()) {
            case VECTOR2 -> new Vector2f((Vector2f) value);
            case VECTOR3 -> new Vector3f((Vector3f) value);
            case VECTOR4 -> new Vector4f((Vector4f) value);
            case QUATERNION -> new Quaternionf((Quaternionf) value);
            case ASSET_REF -> value instanceof AssetRef<?> ref ? ref.path() : (String) value;
            case GAMEOBJECT_REF -> value;
            default -> value;
        };
    }
}
