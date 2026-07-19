package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.Optional;

public interface IComponent {

    void attachTo(GameObject gameObject);

    Optional<GameObject> owner();

    default void onLoad(EngineServices services) {
    }

    default void onPlayStart(EngineServices services) {
    }

    default void onPlayStop(EngineServices services) {
    }
}
