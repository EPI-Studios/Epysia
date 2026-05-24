package fr.epistudio.epysia.editor.reflection;

import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.IComponent;

import java.lang.reflect.Field;

public final class ExportedProperty {

    public enum Kind {
        FLOAT,
        INT,
        BOOLEAN,
        STRING,
        VECTOR3,
        QUATERNION,
        UNKNOWN
    }

    private final IComponent owner;
    private final Field field;
    private final Export annotation;
    private final Kind kind;
    private final String label;

    public ExportedProperty(IComponent owner, Field field, Export annotation, Kind kind) {
        this.owner = owner;
        this.field = field;
        this.annotation = annotation;
        this.kind = kind;
        this.label = annotation.label().isEmpty() ? prettify(field.getName()) : annotation.label();
    }

    public String label() {
        return label;
    }

    public Kind kind() {
        return kind;
    }

    public float min() {
        return annotation.min();
    }

    public float max() {
        return annotation.max();
    }

    public float step() {
        return annotation.step();
    }

    public Object read() {
        try {
            return field.get(owner);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Cannot read field " + field.getName(), exception);
        }
    }

    public void writeFloat(float value) {
        try {
            field.setFloat(owner, value);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Cannot write float " + field.getName(), exception);
        }
    }

    public void writeInt(int value) {
        try {
            field.setInt(owner, value);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Cannot write int " + field.getName(), exception);
        }
    }

    public void writeBoolean(boolean value) {
        try {
            field.setBoolean(owner, value);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Cannot write boolean " + field.getName(), exception);
        }
    }

    public void writeObject(Object value) {
        try {
            field.set(owner, value);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Cannot write object " + field.getName(), exception);
        }
    }

    public String fieldName() {
        return field.getName();
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
