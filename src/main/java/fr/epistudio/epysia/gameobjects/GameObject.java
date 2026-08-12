package fr.epistudio.epysia.gameobjects;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.RequiresComponent;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.ComponentPresentException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class GameObject implements IGameObject {
    public static final int NOT_A_PREFAB_INSTANCE = -1;

    private static final Runnable NO_LISTENER = () -> {
    };

    private final UUID id;
    private String name;
    private final Set<String> tags = new LinkedHashSet<>();
    private boolean active = true;
    private boolean alive = true;
    private boolean persistent = true;
    private boolean keepOnSceneChange;
    private String sourceId = "";
    private String prefabSource = "";
    private int prefabObjectId = NOT_A_PREFAB_INSTANCE;
    private Set<String> overriddenProperties = Set.of();
    private final Map<Class<?>, IComponent> componentsByType = new HashMap<>();
    private Transform3D cachedTransform3D;
    private final List<IComponent> attachedComponents = new ArrayList<>();
    private final List<Object> unloadableComponentPayloads = new ArrayList<>();
    private Runnable structuralChangeListener = NO_LISTENER;

    public List<Object> unloadableComponentPayloads() {
        return unloadableComponentPayloads;
    }

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
        return tags.isEmpty() ? "" : tags.iterator().next();
    }

    public GameObject setTag(String tag) {
        tags.clear();
        structuralChangeListener.run();
        return addTag(tag);
    }

    public Set<String> tags() {
        return Collections.unmodifiableSet(tags);
    }

    public GameObject addTag(String tag) {
        if (tag != null && !tag.isBlank() && tags.add(tag)) {
            structuralChangeListener.run();
        }
        return this;
    }

    public GameObject removeTag(String tag) {
        if (tags.remove(tag)) {
            structuralChangeListener.run();
        }
        return this;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public boolean active() {
        return active;
    }

    public boolean isAlive() {
        return alive;
    }

    public void markDestroyed() {
        alive = false;
        for (IComponent component : attachedComponents) {
            if (component instanceof Component owned) {
                owned.markDestroyed();
            }
        }
    }

    public boolean activeInHierarchy() {
        GameObject current = this;
        while (current != null) {
            if (!current.alive || !current.active) {
                return false;
            }
            current = current.parentOrNull();
        }
        return true;
    }

    public GameObject parentOrNull() {
        if (cachedTransform3D == null) {
            return null;
        }
        Transform3D parentTransform = cachedTransform3D.parentOrNull();
        return parentTransform == null ? null : parentTransform.ownerOrNull();
    }

    public boolean persistent() {
        return persistent;
    }

    public GameObject setPersistent(boolean value) {
        this.persistent = value;
        return this;
    }

    public boolean keepOnSceneChange() {
        return keepOnSceneChange;
    }

    public GameObject setKeepOnSceneChange(boolean value) {
        this.keepOnSceneChange = value;
        return this;
    }

    public String sourceId() {
        return sourceId;
    }

    public GameObject setSourceId(String value) {
        this.sourceId = value == null ? "" : value;
        return this;
    }

    public String prefabSource() {
        return prefabSource;
    }

    public int prefabObjectId() {
        return prefabObjectId;
    }

    public boolean isPrefabInstance() {
        return !prefabSource.isEmpty() && prefabObjectId != NOT_A_PREFAB_INSTANCE;
    }

    public GameObject linkToPrefab(String source, int objectId) {
        this.prefabSource = source == null ? "" : source;
        this.prefabObjectId = objectId;
        return this;
    }

    public GameObject unlinkFromPrefab() {
        this.prefabSource = "";
        this.prefabObjectId = NOT_A_PREFAB_INSTANCE;
        this.overriddenProperties = Set.of();
        return this;
    }

    public static String overrideKey(Class<?> componentClass, String fieldName) {
        return componentClass.getName() + "." + fieldName;
    }

    public Set<String> overriddenProperties() {
        return Collections.unmodifiableSet(overriddenProperties);
    }

    public boolean isOverridden(Class<?> componentClass, String fieldName) {
        return overriddenProperties.contains(overrideKey(componentClass, fieldName));
    }

    public GameObject markOverridden(Class<?> componentClass, String fieldName) {
        return markOverridden(overrideKey(componentClass, fieldName));
    }

    public GameObject markOverridden(String key) {
        if (overriddenProperties.isEmpty()) {
            overriddenProperties = new LinkedHashSet<>();
        }
        overriddenProperties.add(key);
        return this;
    }

    public GameObject clearOverrides() {
        overriddenProperties = Set.of();
        return this;
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
            structuralChangeListener.run();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IComponent> Optional<T> getComponent(Class<T> componentClass) {
        return Optional.ofNullable((T) componentsByType.get(componentClass));
    }

    public Transform3D transform3DOrNull() {
        return cachedTransform3D;
    }

    public boolean setParent(GameObject parent) {
        if (cachedTransform3D == null || parent.cachedTransform3D == null) {
            return false;
        }
        return cachedTransform3D.setParent(parent.cachedTransform3D);
    }

    public boolean addChild(GameObject child) {
        return child.setParent(this);
    }

    public void detachFromParent() {
        if (cachedTransform3D != null) {
            cachedTransform3D.detachFromParent();
        }
    }

    public Optional<GameObject> parent() {
        if (cachedTransform3D == null) {
            return Optional.empty();
        }
        return cachedTransform3D.parent().flatMap(Transform3D::owner);
    }

    public List<GameObject> children() {
        if (cachedTransform3D == null) {
            return List.of();
        }
        List<GameObject> found = new ArrayList<>();
        for (Transform3D child : cachedTransform3D.children()) {
            child.owner().ifPresent(found::add);
        }
        return List.copyOf(found);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IComponent> T getComponentOrNull(Class<T> componentClass) {
        return (T) componentsByType.get(componentClass);
    }

    public <T extends IComponent> List<T> getComponents(Class<T> componentClass) {
        List<T> matches = new ArrayList<>();
        for (IComponent component : attachedComponents) {
            if (componentClass.isInstance(component)) {
                matches.add(componentClass.cast(component));
            }
        }
        return List.copyOf(matches);
    }

    public <T extends IComponent> Optional<T> getComponentInChildren(Class<T> componentClass) {
        List<T> matches = getComponentsInChildren(componentClass);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
    }

    public <T extends IComponent> List<T> getComponentsInChildren(Class<T> componentClass) {
        List<T> matches = new ArrayList<>(getComponents(componentClass));
        for (GameObject child : children()) {
            matches.addAll(child.getComponentsInChildren(componentClass));
        }
        return List.copyOf(matches);
    }

    public <T extends IComponent> Optional<T> getComponentInParent(Class<T> componentClass) {
        List<T> matches = getComponentsInParent(componentClass);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
    }

    public <T extends IComponent> List<T> getComponentsInParent(Class<T> componentClass) {
        List<T> matches = new ArrayList<>(getComponents(componentClass));
        parent().ifPresent(owner -> matches.addAll(owner.getComponentsInParent(componentClass)));
        return List.copyOf(matches);
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
            if (currentClass == Transform3D.class) {
                cachedTransform3D = (Transform3D) componentsByType.get(Transform3D.class);
            }
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
                if (currentClass == Transform3D.class) {
                    cachedTransform3D = null;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }
}
