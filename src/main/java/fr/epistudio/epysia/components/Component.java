package fr.epistudio.epysia.components;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.Optional;

public abstract class Component implements IComponent {

    private GameObject gameObject;

    @Override
    public final void attachTo(GameObject gameObject) {
        if (this.gameObject != null) {
            throw new EpysiaException("Component is already attached to a GameObject.");
        }
        this.gameObject = gameObject;
    }

    @Override
    public final Optional<GameObject> owner() {
        return Optional.ofNullable(gameObject);
    }
}
