package fr.epistudio.epysia.gameobjects;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.exceptions.ComponentPresentException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GameObject implements IGameObject {

    private String name;
    private final Map<Class<?>, IComponent> componentsByType = new HashMap<>();
    private final List<IComponent> attachedComponents = new ArrayList<>();

    public GameObject(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
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
        ensureRequiredComponents(component.getClass());
        component.attachTo(this);
        registerUnderHierarchy(component);
        attachedComponents.add(component);
        return component;
    }

    private void ensureRequiredComponents(Class<?> componentClass) {
        RequiresComponent requires = componentClass.getAnnotation(RequiresComponent.class);
        if (requires == null) {
            return;
        }
        for (Class<? extends IComponent> dependency : requires.value()) {
            if (componentsByType.containsKey(dependency)) {
                continue;
            }
            addComponent(instantiateDefault(dependency));
        }
    }

    private IComponent instantiateDefault(Class<? extends IComponent> dependency) {
        try {
            return dependency.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                    "Cannot auto-create required component " + dependency.getName()
                            + ": no public no-arg constructor available",
                    error);
        }
    }

    public List<IComponent> components() {
        return Collections.unmodifiableList(attachedComponents);
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
        attachedComponents.remove(component);
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
