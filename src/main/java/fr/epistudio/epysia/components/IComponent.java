package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.Optional;

public interface IComponent {
    void attachTo(GameObject gameObject);

    Optional<GameObject> owner();

    default GameObject ownerOrNull() {
        return owner().orElse(null);
    }

    default void onLoad(EngineServices services) {
    }

    default void copyStateFrom(IComponent source) {
    }

    default void onReplicatedStateCapture() {
    }

    default void onReplicatedStateApplied() {
    }

    default void onPlayStart(EngineServices services) {
    }

    default void onPlayStop(EngineServices services) {
    }

    default void onDestroy(EngineServices services) {
    }
}
