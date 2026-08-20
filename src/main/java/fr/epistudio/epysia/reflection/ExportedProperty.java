package fr.epistudio.epysia.reflection;

import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.HiddenInEditor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ExportedProperty {
    public enum Kind {
        FLOAT,
        INT,
        BOOLEAN,
        STRING,
        VECTOR2,
        VECTOR3,
        VECTOR4,
        SURFACE_UNIFORMS,
        QUATERNION,
        ENUM,
        ASSET_REF,
        GAMEOBJECT_REF,
        OBJECT_LIST,
        UNKNOWN
    }

    private final Object owner;
    private final Field field;
    private final PropertyBinding binding;
    private final Kind kind;
    private final String label;

    public ExportedProperty(Object owner, Field field, Export annotation, Kind kind) {
        this.owner = owner;
        this.field = field;
        this.binding = new FieldBinding(owner, field, annotation);
        this.kind = kind;
        this.label = annotation.label().isEmpty() ? prettify(field.getName()) : annotation.label();
    }

    public ExportedProperty(Object owner, PropertyBinding binding, Kind kind) {
        this.owner = owner;
        this.field = null;
        this.binding = binding;
        this.kind = kind;
        this.label = binding.label().isEmpty() ? prettify(binding.name()) : binding.label();
    }

    public String label() {
        return label;
    }

    public Kind kind() {
        return kind;
    }

    public float min() {
        return binding.min();
    }

    public float max() {
        return binding.max();
    }

    public float step() {
        return binding.step();
    }

    public boolean isColor() {
        return binding.color();
    }

    public String[] assetExtensions() {
        return binding.assetExtensions();
    }

    public boolean isLayerMask() {
        return binding.layerMask();
    }

    public Object read() {
        return binding.read();
    }

    public void writeFloat(float value) {
        binding.write(value);
    }

    public void writeInt(int value) {
        binding.write(value);
    }

    public void writeBoolean(boolean value) {
        binding.write(value);
    }

    public void writeObject(Object value) {
        binding.write(value);
    }

    public Object owner() {
        return owner;
    }

    public String fieldName() {
        return binding.name();
    }

    public Class<?> fieldType() {
        return binding.type();
    }

    public Optional<Class<?>> elementType() {
        return field == null ? binding.elementType() : Reflection.elementTypeOf(field);
    }

    public boolean isHiddenInEditor() {
        return binding.hidden();
    }

    public Object[] enumConstants() {
        Class<?> type = binding.type();
        if (!type.isEnum()) {
            return new Object[0];
        }
        List<Object> visible = new ArrayList<>();
        for (Object constant : type.getEnumConstants()) {
            if (!isHiddenEnumConstant(type, (Enum<?>) constant)) {
                visible.add(constant);
            }
        }
        return visible.toArray();
    }

    private static boolean isHiddenEnumConstant(Class<?> type, Enum<?> constant) {
        try {
            return type.getField(constant.name())
                    .isAnnotationPresent(HiddenInEditor.class);
        } catch (NoSuchFieldException missing) {
            return false;
        }
    }

    private static String prettify(String fieldName) {
        StringBuilder result = new StringBuilder(fieldName.length() + 4);
        for (int index = 0; index < fieldName.length(); index++) {
            char character = fieldName.charAt(index);
            if (index == 0) {
                result.append(Character.toUpperCase(character));
                continue;
            }
            if (Character.isUpperCase(character)) {
                result.append(' ');
            }
            result.append(character);
        }
        return result.toString();
    }
}
