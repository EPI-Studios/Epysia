package fr.epistudio.epysia.gameobjects;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.ComponentPresentException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GameObject implements IGameObject {

    private static final Runnable NO_LISTENER = () -> {
    };

    private final UUID id;
    private String name;
    private String tag = "";
    private boolean active = true;
    private final Map<Class<?>, IComponent> componentsByType = new HashMap<>();
    private final List<IComponent> attachedComponents = new ArrayList<>();
    private Runnable structuralChangeListener = NO_LISTENER;

    public GameObject(String name) {
        this(name, UUID.randomUUID());
    }

    public GameObject(String name, UUID id) {
        this.name = name;
        this.id = id;
    }

    public UUID id() {
        return id;
    }

    public void setStructuralChangeListener(Runnable listener) {
        this.structuralChangeListener = listener == null ? NO_LISTENER : listener;
    }

    public void clearStructuralChangeListener() {
        this.structuralChangeListener = NO_LISTENER;
    }

    public String tag() {
        return tag;
    }

    public GameObject setTag(String tag) {
        this.tag = tag == null ? "" : tag;
        return this;
    }

    public boolean active() {
        return active;
    }

    public GameObject setActive(boolean active) {
        this.active = active;
        return this;
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
    @SuppressWarnings("unchecked")
    public <T extends IComponent> T getComponentOrNull(Class<T> componentClass) {
        return (T) componentsByType.get(componentClass);
    }

    @Override
    public <T extends IComponent> T addComponent(T component) {
        if (componentsByType.containsKey(component.getClass())) {
            throw new ComponentPresentException(component, this);
        }
        rejectConflictingTransform(component);
        ensureRequiredComponents(component.getClass());
        component.attachTo(this);
        registerUnderHierarchy(component);
        attachedComponents.add(component);
        structuralChangeListener.run();
        return component;
    }

    private void rejectConflictingTransform(IComponent component) {
        if (component instanceof Transform2D && componentsByType.containsKey(Transform3D.class)) {
            throw new ComponentPresentException(component, this,
                    "A GameObject cannot hold both Transform2D and Transform3D.");
        }
        if (component instanceof Transform3D && componentsByType.containsKey(Transform2D.class)) {
            throw new ComponentPresentException(component, this,
                    "A GameObject cannot hold both Transform2D and Transform3D.");
        }
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

    public void replaceComponent(IComponent existing, IComponent replacement) {
        int index = attachedComponents.indexOf(existing);
        if (index < 0) {
            return;
        }
        unregisterUnderHierarchy(existing);
        attachedComponents.set(index, replacement);
        replacement.attachTo(this);
        registerUnderHierarchy(replacement);
        structuralChangeListener.run();
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
        structuralChangeListener.run();
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
