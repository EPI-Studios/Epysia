package fr.epistudio.epysia.reflection;

import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import fr.epistudio.epysia.render.shader.ShaderUniformValues;
import org.joml.Vector4f;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.EditorAction;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Reflection {
    private Reflection() {
    }

    public static List<ExportedProperty> scan(Object owner) {
        List<ExportedProperty> properties = new ArrayList<>();
        Class<?> currentClass = owner.getClass();
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                Export annotation = field.getAnnotation(Export.class);
                if (annotation == null) {
                    continue;
                }
                ExportedProperty.Kind kind = classifyField(field);
                if (kind == ExportedProperty.Kind.UNKNOWN) {
                    continue;
                }
                field.setAccessible(true);
                properties.add(new ExportedProperty(owner, field, annotation, kind));
            }
            currentClass = currentClass.getSuperclass();
        }
        return properties;
    }

    public static List<ComponentAction> actionsOf(Object owner) {
        List<ComponentAction> actions = new ArrayList<>();
        Class<?> currentClass = owner.getClass();
        while (currentClass != null && currentClass != Object.class) {
            for (Method method : currentClass.getDeclaredMethods()) {
                EditorAction annotation = method.getAnnotation(EditorAction.class);
                if (annotation != null && acceptsAction(method)) {
                    actions.add(new ComponentAction(labelOf(annotation, method), annotation.tooltip(), method));
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return actions;
    }

    private static boolean acceptsAction(Method method) {
        if (method.getParameterCount() == 0) {
            return true;
        }
        return method.getParameterCount() == 1
                && method.getParameterTypes()[0] == EngineServices.class;
    }

    private static String labelOf(EditorAction annotation, Method method) {
        return annotation.label().isBlank() ? method.getName() : annotation.label();
    }

    private static ExportedProperty.Kind classifyField(Field field) {
        if (List.class.isAssignableFrom(field.getType())) {
            return elementTypeOf(field).isPresent()
                    ? ExportedProperty.Kind.OBJECT_LIST : ExportedProperty.Kind.UNKNOWN;
        }
        return classifyKind(field.getType());
    }

    public static Optional<Class<?>> elementTypeOf(Field field) {
        if (!(field.getGenericType() instanceof ParameterizedType parameterized)) {
            return Optional.empty();
        }
        Type[] arguments = parameterized.getActualTypeArguments();
        if (arguments.length != 1 || !(arguments[0] instanceof Class<?> elementType)) {
            return Optional.empty();
        }
        return hasNoArgumentConstructor(elementType) ? Optional.of(elementType) : Optional.empty();
    }

    private static boolean hasNoArgumentConstructor(Class<?> elementType) {
        try {
            elementType.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }

    public static ExportedProperty.Kind kindOf(Class<?> type) {
        return classifyKind(type);
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
        if (type == Vector4f.class) {
            return ExportedProperty.Kind.VECTOR4;
        }
        if (type == ShaderUniformValues.class) {
            return ExportedProperty.Kind.SURFACE_UNIFORMS;
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
