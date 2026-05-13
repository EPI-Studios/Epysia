package fr.epistudio.epysia.gameobjects;

import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform;
import fr.epistudio.epysia.exceptions.ComponentPresentException;

import java.util.HashSet;
import java.util.Set;

public class GameObject implements IGameObject {

    private Set<IComponent> components;
    private String name;
    private transient Transform transform;

    public GameObject(String name, Transform transform){
        this.components = new HashSet<>();
        this.name = name;
        this.transform = transform;
        components.add(transform);
    }

    public final void Init() {

        onInit();
    }

    @Override
    public void onInit() {

    }

    public final void update(float dt) {
        for (IComponent component : components) {
            ((Component) component).update(dt);
        }
        onUpdate(dt);
    }

    @Override
    public void onUpdate(float dt) {

    }

    public final void destroy() {
        for (IComponent component : components){
            ((Component) component).destroy();
        }
        onDestroy();
    }

    @Override
    public void onDestroy() {

    }


    @Override
    public <T extends IComponent> T getComponent(Class<T> componentClass) {
        for (IComponent component : components) {
            if (componentClass.isInstance(component)) {
                return componentClass.cast(component);
            }
        }
        return null;
    }

    @Override
    public <T extends IComponent> T addComponent(T component) {
        if (getComponent(component.getClass()) != null) {
            throw new ComponentPresentException(component, this);
        }
        component.setGameObject(this);
        components.add(component);
        return component;
    }

    @Override
    public <T extends IComponent> T removeComponent(Class<T> componentClass) {
        T component = getComponent(componentClass);
        if (component != null) {
            components.remove(component);
        }
        return component;
    }

    @Override
    public String getName() {
        return name;
    }
}
