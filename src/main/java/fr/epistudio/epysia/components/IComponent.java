package fr.epistudio.epysia.components;

import fr.epistudio.epysia.gameobjects.GameObject;

public interface IComponent {

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

    /**
     * Attaches this component to a game object.
     *
     * @param gameObject owning game object
     */
    void setGameObject(GameObject gameObject);

    /**
     * Returns the owning game object.
     *
     * @return owning game object, or {@code null} when the component is not attached
     */
    GameObject getGameObject();

    /**
     * Returns whether this component is attached to a game object.
     *
     * @return {@code true} when the component has an owner
     */
    boolean hasGameObject();

}
