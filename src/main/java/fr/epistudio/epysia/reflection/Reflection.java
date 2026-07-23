package fr.epistudio.epysia.reflection;

import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class Reflection {

    private Reflection() {
    }

    public static List<ExportedProperty> scan(IComponent component) {
        List<ExportedProperty> properties = new ArrayList<>();
        Class<?> currentClass = component.getClass();
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                Export annotation = field.getAnnotation(Export.class);
                if (annotation == null) {
                    continue;
                }
                ExportedProperty.Kind kind = classifyKind(field.getType());
                if (kind == ExportedProperty.Kind.UNKNOWN) {
                    continue;
                }
                field.setAccessible(true);
                properties.add(new ExportedProperty(component, field, annotation, kind));
            }
            currentClass = currentClass.getSuperclass();
        }
        return properties;
    }

    private static ExportedProperty.Kind classifyKind(Class<?> type) {
        if (type == float.class || type == Float.class) {
            return ExportedProperty.Kind.FLOAT;
        }
        if (type == int.class || type == Integer.class) {
            return ExportedProperty.Kind.INT;
        }
        if (type == boolean.class || type == Boolean.class) {
            return ExportedProperty.Kind.BOOLEAN;
        }
        if (type == String.class) {
            return ExportedProperty.Kind.STRING;
        }
        if (type == Vector2f.class) {
            return ExportedProperty.Kind.VECTOR2;
        }
        if (type == Vector3f.class) {
            return ExportedProperty.Kind.VECTOR3;
        }
        if (type == Quaternionf.class) {
            return ExportedProperty.Kind.QUATERNION;
        }
        if (type.isEnum()) {
            return ExportedProperty.Kind.ENUM;
        }
        if (type == AssetRef.class) {
            return ExportedProperty.Kind.ASSET_REF;
        }
        if (GameObject.class.isAssignableFrom(type)) {
            return ExportedProperty.Kind.GAMEOBJECT_REF;
        }
        return ExportedProperty.Kind.UNKNOWN;
    }
}
