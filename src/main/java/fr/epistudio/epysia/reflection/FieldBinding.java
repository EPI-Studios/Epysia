package fr.epistudio.epysia.reflection;

import fr.epistudio.epysia.components.Export;
import fr.epistudio.epysia.components.HiddenInEditor;

import java.lang.reflect.Field;
import java.util.Optional;

final class FieldBinding implements PropertyBinding {

    private final Object owner;
    private final Field field;
    private final Export annotation;

    FieldBinding(Object owner, Field field, Export annotation) {
        this.owner = owner;
        this.field = field;
        this.annotation = annotation;
    }

    @Override
    public String name() {
        return field.getName();
    }

    @Override
    public Class<?> type() {
        return field.getType();
    }

    @Override
    public Object read() {
        try {
            return field.get(owner);
        } catch (IllegalAccessException unreachable) {
            throw new IllegalStateException("Cannot read field " + field.getName(), unreachable);
        }
    }

    @Override
    public void write(Object value) {
        try {
            field.set(owner, value);
        } catch (IllegalAccessException unreachable) {
            throw new IllegalStateException("Cannot write field " + field.getName(), unreachable);
        }
    }

    @Override
    public String label() {
        return annotation.label();
    }

    @Override
    public float min() {
        return annotation.min();
    }

    @Override
    public float max() {
        return annotation.max();
    }

    @Override
    public float step() {
        return annotation.step();
    }

    @Override
    public boolean color() {
        return annotation.color();
    }

    @Override
    public boolean layerMask() {
        return annotation.layerMask();
    }

    @Override
    public boolean hidden() {
        return field.isAnnotationPresent(HiddenInEditor.class);
    }

    @Override
    public String[] assetExtensions() {
        return annotation.assetExtensions();
    }

    @Override
    public Optional<Class<?>> elementType() {
        return Reflection.elementTypeOf(field);
    }
}
