package fr.epistudio.epysia.gameobjects;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.exceptions.ComponentPresentException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class GameObject implements IGameObject {

    private final String name;
    private final Map<Class<?>, IComponent> componentsByType = new HashMap<>();

    public GameObject(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IComponent> Optional<T> getComponent(Class<T> componentClass) {
        return Optional.ofNullable((T) componentsByType.get(componentClass));
    }

    @Override
    public <T extends IComponent> T addComponent(T component) {
        if (componentsByType.containsKey(component.getClass())) {
            throw new ComponentPresentException(component, this);
        }
        component.attachTo(this);
        registerUnderHierarchy(component);
        return component;
    }

    private void registerUnderHierarchy(IComponent component) {
        Class<?> currentClass = component.getClass();
        while (currentClass != null && IComponent.class.isAssignableFrom(currentClass)) {
            componentsByType.putIfAbsent(currentClass, component);
            currentClass = currentClass.getSuperclass();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IComponent> Optional<T> removeComponent(Class<T> componentClass) {
        IComponent component = componentsByType.get(componentClass);
        if (component == null) {
            return Optional.empty();
        }
        unregisterUnderHierarchy(component);
        return Optional.of((T) component);
    }

    private void unregisterUnderHierarchy(IComponent component) {
        Class<?> currentClass = component.getClass();
        while (currentClass != null && IComponent.class.isAssignableFrom(currentClass)) {
            if (componentsByType.get(currentClass) == component) {
                componentsByType.remove(currentClass);
            }
            currentClass = currentClass.getSuperclass();
        }
    }
}
