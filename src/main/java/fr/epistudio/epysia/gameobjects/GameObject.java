package fr.epistudio.epysia.gameobjects;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.exceptions.ComponentPresentException;

import java.util.HashSet;
import java.util.Set;

public class GameObject implements IGameObject {

    private Set<IComponent> components;

    public GameObject(){
        this.components = new HashSet<>();
    }

    public void Init() {

        onInit();
    }

    @Override
    public void onInit() {

    }

    public void update(float dt) {

        onUpdate(dt);
    }

    @Override
    public void onUpdate(float dt) {

    }

    public void destroy() {

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
}
