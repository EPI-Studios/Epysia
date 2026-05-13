package fr.epistudio.epysia.gameobjects;

import fr.epistudio.epysia.components.IComponent;

/**
 * GameObject Interface
 */
public interface IGameObject {

    /**
     * get Component
     * @param componentClass components class to get
     * @return Component or null if not found
     * @param <T> Component type
     */
    <T extends IComponent> T getComponent(Class<T> componentClass);

    /**
     * add Component
     * @param component component to add
     * @return added component
     * @param <T> Component type
     */
    <T extends IComponent> T addComponent(T component);

    /**
     * remove Component
     * @param componentClass components class to remove
     * @return removed component or null if not found
     * @param <T> Component type
     */
    <T extends IComponent> T removeComponent(Class<T> componentClass);

    /**
     * onInit
     */
    void onInit();

    /**
     * onUpdate
     * @param dt delta time since last update
     */
    void onUpdate(float dt);

    /**
     * onDestroy
     */
    void onDestroy();

    String getName();
}
