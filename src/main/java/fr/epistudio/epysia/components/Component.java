package fr.epistudio.epysia.components;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;

public abstract class Component implements IComponent {

    protected transient GameObject gameObject;

    public final void Init() {

        onInit();
    }

    @Override
    public void onInit() {

    }

    public final void update(float dt) {

        onUpdate(dt);
    }

    @Override
    public void onUpdate(float dt) {

    }

    public final void destroy() {

        onDestroy();
    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void setGameObject(GameObject gameObject) {
        if (this.gameObject != null) {
            throw new EpysiaException("GameObject is already set for this component.");
        }
        this.gameObject = gameObject;
    }

    @Override
    public GameObject getGameObject() {
        return gameObject;
    }

    @Override
    public boolean hasGameObject() {
        return gameObject != null;
    }

}
