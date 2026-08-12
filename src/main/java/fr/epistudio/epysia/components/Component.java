package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRefFields;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.Optional;

public abstract class Component implements IComponent {

    private GameObject gameObject;
    private boolean enabled = true;
    private boolean alive = true;

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

    @Override
    public final GameObject ownerOrNull() {
        return gameObject;
    }

    @Override
    public final boolean enabled() {
        return enabled;
    }

    @Override
    public final void setEnabled(boolean value) {
        enabled = value;
    }

    @Override
    public final boolean isAlive() {
        return alive;
    }

    public final void markDestroyed() {
        alive = false;
    }

    @Override
    public void onDestroy(EngineServices services) {
        AssetRefFields.releaseAll(this);
    }
}
